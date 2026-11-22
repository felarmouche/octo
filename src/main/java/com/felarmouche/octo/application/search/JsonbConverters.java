package com.felarmouche.octo.application.search;

import org.postgresql.util.PGobject;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.convert.converter.Converter;
import org.springframework.data.convert.ReadingConverter;
import org.springframework.data.convert.WritingConverter;
import org.springframework.data.jdbc.repository.config.AbstractJdbcConfiguration;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.sql.SQLException;
import java.util.List;

/** Maps {@link JsonNode} properties onto PostgreSQL jsonb columns for Spring Data JDBC. */
@Configuration
class JsonbConverters extends AbstractJdbcConfiguration {

    private static final JsonMapper JSON = JsonMapper.builder().build();

    @Override
    protected List<?> userConverters() {
        return List.of(new ToJsonb(), new FromJsonb());
    }

    @WritingConverter
    static class ToJsonb implements Converter<JsonNode, PGobject> {
        @Override
        public PGobject convert(JsonNode source) {
            try {
                PGobject jsonb = new PGobject();
                jsonb.setType("jsonb");
                jsonb.setValue(JSON.writeValueAsString(source));
                return jsonb;
            } catch (SQLException e) {
                throw new IllegalStateException(e);
            }
        }
    }

    @ReadingConverter
    static class FromJsonb implements Converter<PGobject, JsonNode> {
        @Override
        public JsonNode convert(PGobject source) {
            return JSON.readTree(source.getValue());
        }
    }
}
