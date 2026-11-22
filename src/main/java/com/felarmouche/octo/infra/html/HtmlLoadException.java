package com.felarmouche.octo.infra.html;

public class HtmlLoadException extends RuntimeException {
    public HtmlLoadException(String url, Throwable cause) {
        super("Seite " + url + " nicht ladbar: " + cause.getMessage(), cause);
    }
}
