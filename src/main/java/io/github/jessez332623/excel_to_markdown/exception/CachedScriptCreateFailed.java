package io.github.jessez332623.excel_to_markdown.exception;

/** 创建缓存脚本失败时最终会 re-throw 成本异常。*/
public class CachedScriptCreateFailed
    extends RuntimeException
{
    public CachedScriptCreateFailed(String message) {
        super(message);
    }

    public CachedScriptCreateFailed(String message, Throwable cause) {
        super(message, cause);
    }
}
