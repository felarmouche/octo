package com.felarmouche.octo.application.settings;

import com.github.victools.jsonschema.generator.CustomDefinition;
import com.github.victools.jsonschema.generator.FieldScope;
import com.github.victools.jsonschema.generator.MemberScope;
import com.github.victools.jsonschema.generator.Option;
import com.github.victools.jsonschema.generator.OptionPreset;
import com.github.victools.jsonschema.generator.SchemaGenerator;
import com.github.victools.jsonschema.generator.SchemaGeneratorConfigBuilder;
import com.github.victools.jsonschema.generator.SchemaVersion;
import com.github.victools.jsonschema.module.jackson.JacksonSchemaModule;
import com.github.victools.jsonschema.module.jakarta.validation.JakartaValidationModule;
import com.github.victools.jsonschema.module.jakarta.validation.JakartaValidationOption;
import com.github.victools.jsonschema.module.swagger2.Swagger2Module;
import io.swagger.v3.oas.annotations.media.Schema;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;

import java.lang.reflect.Method;
import java.lang.reflect.RecordComponent;
import java.util.Comparator;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * The JSON Schema of a settings record, so a frontend can render its form without knowing the Plugin:
 * <ul>
 *   <li>types and required fields from the record (primitives are required, like {@link SettingsCodec} decodes them),</li>
 *   <li>constraints from its Bean Validation annotations,</li>
 *   <li>labels ({@code title}) and initial values ({@code default}) from {@code @Schema(title, defaultValue)},</li>
 *   <li>the input a frontend shows ({@code format}) from {@code @Schema(format)}, e.g. {@code money-cents}
 *       for an integer amount in cents that a frontend edits in euros,</li>
 *   <li>enum value labels ({@code oneOf} of {@code const} and {@code title}) from the enum's {@code label()} method.</li>
 * </ul>
 */
@Component
public class SettingsSchema {

    private static final JsonMapper JSON = JsonMapper.builder().build();

    private final SchemaGenerator generator;
    private final Map<Class<?>, JsonNode> cache = new ConcurrentHashMap<>();

    public SettingsSchema() {
        SchemaGeneratorConfigBuilder config = new SchemaGeneratorConfigBuilder(SchemaVersion.DRAFT_2020_12, OptionPreset.PLAIN_JSON)
                .with(Option.FORBIDDEN_ADDITIONAL_PROPERTIES_BY_DEFAULT);
        // before the Swagger module, whose default is always a string
        config.forFields()
                .withDefaultResolver(SettingsSchema::typedDefault)
                .withRequiredCheck(field -> field.getType().getErasedType().isPrimitive())
                .withInstanceAttributeOverride((attributes, field, context) -> {
                    Schema schema = field.getAnnotationConsideringFieldAndGetter(Schema.class);
                    if (schema != null && !schema.format().isEmpty()) attributes.put("format", schema.format());
                });
        config.forTypesInGeneral().withCustomDefinitionProvider((type, context) -> {
            Class<?> erased = type.getErasedType();
            return erased.isEnum() ? labelledEnum(erased, context.getGeneratorConfig().createObjectNode()) : null;
        });
        config.with(new JacksonSchemaModule())
                .with(new JakartaValidationModule(JakartaValidationOption.NOT_NULLABLE_FIELD_IS_REQUIRED,
                        JakartaValidationOption.INCLUDE_PATTERN_EXPRESSIONS))
                .with(new Swagger2Module());
        // after the modules, which bring their own sorter: a form shows the fields as the record declares them
        config.forTypesInGeneral().withPropertySorter(Comparator.comparingInt(SettingsSchema::declarationIndex));
        this.generator = new SchemaGenerator(config.build());
    }

    public JsonNode of(Class<? extends Record> settingsType) {
        return cache.computeIfAbsent(settingsType, generator::generateSchema);
    }

    private static int declarationIndex(MemberScope<?, ?> member) {
        RecordComponent[] components = member.getDeclaringType().getErasedType().getRecordComponents();
        for (int i = 0; components != null && i < components.length; i++)
            if (components[i].getName().equals(member.getDeclaredName())) return i;
        return Integer.MAX_VALUE;
    }

    /** A number or boolean default as such, not as the string it is written as in the annotation. */
    private static Object typedDefault(FieldScope field) {
        Schema schema = field.getAnnotationConsideringFieldAndGetter(Schema.class);
        if (schema == null || schema.defaultValue().isEmpty()) return null;
        Class<?> type = field.getType().getErasedType();
        boolean literal = type.isPrimitive() || Number.class.isAssignableFrom(type) || type == Boolean.class;
        return literal ? JSON.readTree(schema.defaultValue()) : schema.defaultValue();
    }

    /** {@code {"type": "string", "oneOf": [{"const": "GB_64", "title": "64 GB"}, ...]}}; null without {@code label()}. */
    private static CustomDefinition labelledEnum(Class<?> enumType, ObjectNode definition) {
        Method label;
        try {
            label = enumType.getMethod("label");
        } catch (NoSuchMethodException e) {
            return null;
        }
        definition.put("type", "string");
        ArrayNode values = definition.putArray("oneOf");
        for (Object constant : enumType.getEnumConstants()) {
            try {
                values.addObject().put("const", ((Enum<?>) constant).name()).put("title", (String) label.invoke(constant));
            } catch (ReflectiveOperationException e) {
                throw new IllegalStateException("label() of " + constant, e);
            }
        }
        return new CustomDefinition(definition);
    }
}
