package com.example.blogger.exception;

public class ArticleParseException extends RuntimeException {

    public ArticleParseException(String message) {
        super(message);
    }

    public ArticleParseException(String message, Throwable cause) {
        super(message, cause);
    }
}
