package cn.jesse.excel_to_markdown.service_pool.utils;

import cn.jesse.excel_to_markdown.service_pool.exception.CachedScriptCreateFailed;
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
    /** 缓存的临时脚本文件路径实例。*/
    private static Path cachedTempScript;

    /**
     * 从 classpath 中读取脚本，写入系统默认的临时文件路径进行缓存。
     *
     * @param scriptClasspath 脚本所在的 classpath
     *
     * @return 脚本在系统默认临时文件夹的 path
     */
    public static Path
    createCachedScript(String scriptClasspath)
    {
        synchronized (CachedScriptCreator.class)
        {
            if (cachedTempScript != null && Files.exists(cachedTempScript)) {
                return cachedTempScript;
            }

            try (InputStream scriptStream = CachedScriptCreator.class.getResourceAsStream(scriptClasspath))
            {
                if (scriptStream == null)
                {
                    final String errorMessage
                        = String.format("Script from classpath: %s Not found!", scriptClasspath);

                    log.error(errorMessage);

                    throw new
                    CachedScriptCreateFailed(errorMessage);
                }

                cachedTempScript
                    = Files.createTempFile("table_converter", ".py");

                Files.copy(scriptStream, cachedTempScript, StandardCopyOption.REPLACE_EXISTING);

                /* 注册一个钩子：在应用关闭时删除缓存的临时文件。*/
                Runtime.getRuntime()
                    .addShutdownHook(
                        new Thread(() -> {
                            try { Files.deleteIfExists(cachedTempScript); }
                            catch (IOException e) {
                                log.warn("Can not delete temp file: {}", cachedTempScript, e);
                            }
                        })
                    );

                return cachedTempScript;
            }
            catch (IOException exception)
            {
                log.error(
                    "Exception occurred during creating cached script!",
                    exception
                );

                throw new
                CachedScriptCreateFailed(
                    "Exception occurred during creating cached script!",
                    exception
                );
            }
        }
    }
}
