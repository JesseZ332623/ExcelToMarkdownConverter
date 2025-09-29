package io.github.jessez332623.excel_to_markdown.exception.exports;

/** 在转换过程中出现的任何异常，最终都会 re-throw 成本异常。*/
public class ScriptWorkerException extends RuntimeException
{
    public ScriptWorkerException(String message) {
        super(message);
    }

    public ScriptWorkerException(String message, Throwable throwable) {
        super(message, throwable);
    }
}
