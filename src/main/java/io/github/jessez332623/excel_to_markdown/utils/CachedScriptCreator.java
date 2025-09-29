package io.github.jessez332623.excel_to_markdown.utils;

import io.github.jessez332623.excel_to_markdown.exception.CachedScriptCreateFailed;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

/** 缓存脚本创建器。*/
@Slf4j
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class CachedScriptCreator
{
    /** 缓存脚本路径 */
    private static volatile Path cachedTempScript;

    /** 从指定 classpath 加载脚本，并缓存到默认临时文件目录中去。*/
    public static Path
    createCachedScript(String scriptClasspath)
    {
        Path currentCache = cachedTempScript;

        // 第一次检查，不加锁
        if (isValidCache(currentCache)) {
            return currentCache;
        }

        /*
         * 若多个线程同时进入 createCachedScript() 方法，
         * 在应用启动时可能都会得出 “缓存脚本不存在” 的结论，
         * 如果这里没有监视锁，可能就会有多个线程尝试执行缓存脚本操作，造成脚本内容 “重叠”。
         *
         * 要说这样的设计有无瓶颈，答案或许是有的：
         * 在脚本还未缓存的时候可能会有大量线程执行到此处被阻塞，
         * 等待第一个线程创建完脚本缓存后再拿锁进行第二次检查，可能会有微小的延迟。
         * 其次，如果有哪个二货在应用运行时删除了缓存的脚本，这个微小延迟或许会再次出现。
         */
        synchronized (CachedScriptCreator.class) {
            currentCache = cachedTempScript;

            // 上锁后的第二次检查
            if (isValidCache(currentCache)) {
                return currentCache;
            }

            return createNewCache(scriptClasspath);
        }
    }

    /** 脚本是否已经缓存？ */
    private static boolean
    isValidCache(Path cache)
    {
        if (cache == null) {
            return false;
        }

        try {
            return Files.exists(cache);
        }
        catch (SecurityException e)
        {
            log.debug("Cannot check cache file existence", e);
            return false;
        }
    }

    /** 从指定 classpath 加载脚本，并返回该脚本文件的输入流。 */
    private static InputStream
    getScriptIStream(String scriptClasspath)
    {
        return
        CachedScriptCreator.class
            .getResourceAsStream(scriptClasspath);
    }

    /**
     * 从指定 classpath 加载脚本，并缓存到默认临时文件目录中去。
     *（针对 volatile 关键字进行了优化，使用 “快照”，避免频繁的读写 volatile 变量）
     */
    private static Path
    createNewCache(String scriptClasspath)
    {
        try (InputStream scriptStream = getScriptIStream(scriptClasspath))
        {
            // 在 classpath 指向的资源不存在时，scriptStream 为空，加载脚本失败
            if (scriptStream == null)
            {
                final String errorMessage
                    = String.format("Script from classpath: %s Not found!", scriptClasspath);

                log.error(errorMessage);

                throw new
                CachedScriptCreateFailed(errorMessage);
            }

            // 创建缓存文件并写入
            Path newCache
                = Files.createTempFile("table_converter", ".py");
            Files.copy(scriptStream, newCache, StandardCopyOption.REPLACE_EXISTING);

            // 注册关闭钩子（在 JVM 关闭时尝试删除缓存脚本）
            Runtime.getRuntime()
                   .addShutdownHook(
                       new Thread(() -> {
                            try { Files.deleteIfExists(newCache); }
                            catch (IOException e) {
                                log.warn("Cannot delete temp file: {}", newCache, e);
                            }
                       })
                   );

            // 更新缓存脚本变量（仅更新一次 volatile 变量）
            cachedTempScript = newCache;

            return newCache;

        }
        catch (IOException exception)
        {
            log.error("Exception occurred during creating cached script!", exception);

            throw new
            CachedScriptCreateFailed(
                "Exception occurred during creating cached script!", exception
            );
        }
    }
}
