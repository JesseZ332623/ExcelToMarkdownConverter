package cn.jesse.excel_to_markdown.service_pool.impl;

import cn.jesse.excel_to_markdown.service_pool.ConvertServicePoolManager;
import cn.jesse.excel_to_markdown.service_pool.exception.CachedScriptCreateFailed;
import cn.jesse.excel_to_markdown.service_pool.exception.NotSupportFileExtension;
import cn.jesse.excel_to_markdown.service_pool.exception.ScriptWorkerException;
import cn.jesse.excel_to_markdown.service_pool.utils.CachedScriptCreator;
import cn.jesse.excel_to_markdown.service_pool.utils.FileExtensionChecker;
import jakarta.annotation.PostConstruct;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.springframework.beans.factory.BeanInitializationException;
import org.springframework.beans.factory.DisposableBean;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingDeque;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/** Excel 表格转 Markdown Python 服务池管理器默认实现。*/
@Slf4j
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public class DefaultConvertServicePoolManager
    implements DisposableBean, ConvertServicePoolManager
{
    /* EXCEL 表格文件转 Markdown 文件 python 脚本的 classpath */
    private static final String
    SCRIPT_CLASSPATH = "/py-scripts/table_converter_service.py";

    /** 最大服务进程数量（默认是 4 个）*/
    private int MAX_SERVICE_AMOUNT;

    /** 销毁服务池时，最多给池中的服务多少时间去处理完手头的任务？（默认 15 秒）*/
    private int DESTROY_MAX_WAIT_SECONDS;

    /** 销毁服务池时，每隔多久去检查池中服务的状态？（默认 500 毫秒）*/
    private int DESTROY_WAIT_INTERVAL_MILLIS;

    /** 池子是否正在关闭中？*/
    private volatile boolean isShuttingDown = false;

    /** 正在处理任务的进程数量 */
    private final
    AtomicInteger activeWorkerCount = new AtomicInteger(0);

    /** 所有工作进程的列表，用于关闭时清理 */
    private List<ScriptWorker> allWorkers;

    /** 服务阻塞队列 */
    private final
    BlockingQueue<ScriptWorker>
    idleWorkerQueue = new LinkedBlockingDeque<>();

    public DefaultConvertServicePoolManager(
        int maxService,
        int destroyMaxWaitSeconds,
        int destroyWaitIntervalMillis
    )
    {
        this.MAX_SERVICE_AMOUNT           = maxService;
        this.DESTROY_MAX_WAIT_SECONDS     = destroyMaxWaitSeconds;
        this.DESTROY_WAIT_INTERVAL_MILLIS = destroyWaitIntervalMillis;
    }

    /** 单个服务的抽象 */
    @NoArgsConstructor(access = AccessLevel.PRIVATE)
    private final static class ScriptWorker
    {
        /** 解析结束标志 */
        private static final String END_MARK
            = "@@END_OF_CONVERSION@@";

        /** 解析过程中出现错误标志 */
        private static final String ERROR_MARK
            = "@@END_OF_CONVERSION_ERROR@@";

        /** 解析过程中出现致命错误标志 */
        private static final String FATAL_MARK
            = "fatal";

        /** 解析过程中出现异常标志 */
        private static final String EXCEPTION_MARK
            = "exception";

        /** 服务进程本体 */
        private Process pythonProcess;

        /** 与进程的标准输入进行通信的缓冲区 */
        private BufferedWriter pythonWriter;

        /** 与进程的标准输出进行通信的缓冲区 */
        private BufferedReader pythonReader;

        /** 与进程的标准错误进行通信的缓冲区 */
        private BufferedReader pythonErrorReader;

        /** 检查本服务是否正在运行。*/
        public boolean
        isNotAlive() {
            return Objects.isNull(this.pythonProcess) || !this.pythonProcess.isAlive();
        }

        /** 获取服务的 PID（如果服务进程不存在则返回 -1）。*/
        public long
        getPID()
        {
            if (this.isNotAlive()) {
                return -1;
            }
            else {
                return this.pythonProcess.pid();
            }
        }

        /** 初始化一个转换服务。*/
        public void
        initWorker()
        {
            // 检查是否已经启动，是则直接返回
            if (Objects.nonNull(this.pythonProcess) && this.pythonProcess.isAlive()) {
                return;
            }

            Path cachedScript
                = CachedScriptCreator.createCachedScript(SCRIPT_CLASSPATH);

            ProcessBuilder processBuilder
                = new ProcessBuilder("py", cachedScript.toString());

            // 设置 UTF-8 环境变量，避免中文文件名乱码
            Map<String, String> env = processBuilder.environment();
            env.put("PYTHONUTF8", "1");

            try
            {
                this.pythonProcess
                    = processBuilder.start();
            }
            catch (IOException | CachedScriptCreateFailed exception)
            {
                throw new
                ScriptWorkerException(
                    String.format(
                        "Start Python Service failed, Caused by：%s",
                        exception.getMessage()),
                    exception
                );
            }

            this.pythonReader
                = new BufferedReader(
                new InputStreamReader(
                    this.pythonProcess.getInputStream(),
                    StandardCharsets.UTF_8
                )
            );
            this.pythonWriter
                = new BufferedWriter(
                new OutputStreamWriter(
                    this.pythonProcess.getOutputStream(),
                    StandardCharsets.UTF_8
                )
            );
            this.pythonErrorReader
                = new BufferedReader(
                new InputStreamReader(
                    this.pythonProcess.getErrorStream(),
                    StandardCharsets.UTF_8
                )
            );

            log.info(
                "Python service start success (PID: {}), service run script：{}",
                this.pythonProcess.pid(), SCRIPT_CLASSPATH
            );
        }

        /** 重启转换服务。*/
        private void
        restart() throws InterruptedException
        {
            this.shutdown();
            this.initWorker();
        }

        /** 关闭转换服务。*/
        private boolean
        shutdown()
        {
            long servicePID
                = (this.pythonProcess != null)
                    ? this.pythonProcess.pid()
                    : -1;

            boolean isGracefulShutDown = false;

            if (this.pythonWriter != null)
            {
                try
                {
                    this.pythonWriter.write("exit\n");
                    this.pythonWriter.flush();
                    isGracefulShutDown = true;
                }
                catch (IOException exception)
                {
                    log.warn(
                        "Terminate service (PID: {}) gracefully failed!",
                        servicePID, exception
                    );
                }
            }

            if (Objects.nonNull(this.pythonProcess) && !isGracefulShutDown)
            {
                try
                {
                    // 给服务进程 5 秒的时间完成退出，超过这个时间直接处斩
                    if (!pythonProcess.waitFor(5L, TimeUnit.SECONDS))
                    {
                        this.pythonProcess.destroyForcibly();
                        log.warn("Abort service  (PID: {})", servicePID);
                    }
                }
                catch (InterruptedException e)
                {
                    log.warn(
                        "Waiting for the process to end is interrupted, " +
                        "forcibly terminate the service (PID: {})", servicePID
                    );

                    this.pythonProcess.destroyForcibly();
                    Thread.currentThread().interrupt();   // 线程保持中断状态
                }
                finally {
                    // 不论本服务进程是否正常处死，都丢弃这个进程实例的引用
                    this.pythonProcess = null;
                }
            }

            this.closeBufferQuietly(this.pythonReader);
            this.closeBufferQuietly(this.pythonWriter);
            this.closeBufferQuietly(this.pythonErrorReader);

            this.pythonReader      = null;
            this.pythonWriter      = null;
            this.pythonErrorReader = null;

            return isGracefulShutDown;
        }

        /** “安静的” 关闭与进程通信的 I/O 缓冲流。*/
        private void
        closeBufferQuietly(Closeable closeable)
        {
            if (closeable != null)
            {
                try { closeable.close(); }
                catch (IOException ignore) {}
            }
        }

        /**
         * 向服务提交任务。
         *
         * @param tablePath 表格临时文件路径
         */
        public void
        submit(String tablePath)
            throws IOException, NotSupportFileExtension
        {
            FileExtensionChecker.check(tablePath);

            this.pythonWriter.write(tablePath + "\n");
            this.pythonWriter.flush();
        }

        /** 获取转换结果。*/
        @NotNull
        public String getResult() throws IOException
        {
            String line;
            StringBuilder scriptResult = new StringBuilder();

            boolean hasError = false;

            while ((line = pythonReader.readLine()) != null)
            {
                if (line.equals(END_MARK)) {
                    break;
                }
                if (line.equals(ERROR_MARK)) {
                    hasError = true;
                    continue;
                }

                scriptResult.append(line).append('\n');
            }

            if (hasError)
            {
                log.error(
                    "Python script encountered an error during execution, " +
                    "please check the log for details."
                );
            }

            return
            scriptResult.toString().trim();
        }

        /** 检查本服务在转换过程中可能出现的错误。*/
        public void checkError()
            throws InterruptedException, IOException
        {
            StringBuilder errorResult = new StringBuilder();

            char[] buffer = new char[1024];
            int bytesRead;

            while (this.pythonErrorReader.ready() &&
                (bytesRead = pythonErrorReader.read(buffer)) != -1)
            {
                errorResult.append(buffer, 0, bytesRead);
            }

            if (!errorResult.isEmpty())
            {
                log.error("Exception occurred during convert table, Caused by：{}", errorResult);

                // 如果错误严重，可能需要重启进程
                if (errorResult.toString().contains(FATAL_MARK) ||
                    errorResult.toString().contains(EXCEPTION_MARK))
                {
                    log.warn("Serious error detected, restart Python process.");
                    this.restart();
                }
            }
        }
    }

    /** 初始化转换服务池（服务器开机时自动执行）。*/
    @PostConstruct
    private void
    init()
    {
        List<ScriptWorker> tempAllWorkers = new ArrayList<>();
        for (int index = 0; index < MAX_SERVICE_AMOUNT; ++index)
        {
            try
            {
                ScriptWorker worker = new ScriptWorker();
                worker.initWorker();

                tempAllWorkers.add(worker);
                this.idleWorkerQueue.offer(worker);
            }
            catch (ScriptWorkerException e)
            {
                log.error("{}", e.getMessage(), e);
            }
        }

        this.allWorkers
            = Collections.unmodifiableList(tempAllWorkers);

        // 若所有服务皆启动失败，整个应用程序也不要启动了（怒）
        if (this.idleWorkerQueue.isEmpty())
        {
            throw new
            BeanInitializationException(
                "All Python service init failed, service can not be used!"
            );
        }
    }

    /** 服务池实例销毁前，先销毁池内所有服务。*/
    @Override
    public void destroy()
    {
        this.isShuttingDown = true;

        log.info("Starting to close all Python service ...");

        this.waitingToFinish();

        int closedCount = 0;

        for (ScriptWorker worker : this.allWorkers)
        {
            try
            {
                long pid = worker.getPID();

                // 只有优雅关闭的服务才纳入计数
                if (worker.shutdown()) {
                    closedCount++;
                }

                log.info("Shutdown Python service (PID: {}) success!", pid);
            }
            catch (Exception e)
            {
                log.error(
                    "An error occurred during the shutdown of the Python service (PID: {})",
                    e.getMessage()
                );
            }
        }

        log.info(
            "Success to shutdown {} out of {} services.",
            closedCount, this.allWorkers.size()
        );
    }

    /**
     * 作为 waitingToFinish() 的辅助方法，在等待时间范围内，
     * 每隔一小段时间检查池中的服务是否都已经完成了手头的任务。
     *
     * @param startTime      开始时间戳
     *
     * @return 在本轮检查中，池中的所有任务是否都处于空闲状态？  <br/>
     *         true  当前仍有服务在处理任务，需要进行下一轮检查  <br/>
     *         false 当前所有任务都已空闲
     */
    private boolean
    isCompletelyFinish(final long startTime)
    {
        return
        this.activeWorkerCount.get() > 0 &&
        ((System.currentTimeMillis() - startTime) < DESTROY_MAX_WAIT_SECONDS * 1000L);
    }

    /** 在关闭服务池前，先等待所有服务处理完手头的任务。*/
    private void waitingToFinish()
    {
        final long startTime = System.currentTimeMillis();

        while (this.isCompletelyFinish(startTime))
        {
            try {
                Thread.sleep(DESTROY_WAIT_INTERVAL_MILLIS);
            }
            catch (InterruptedException exception)
            {
                Thread.currentThread().interrupt();

                log.warn("Interrupted while waiting for workers to finish.");
                break;
            }
        }

        /*
         * 若在 maxWaitSeconds 之后还有服务繁忙（这种情况少见），
         * 只得强制关闭了。
         */
        if (this.activeWorkerCount.get() > 0)
        {
            log.warn(
                "Still have {} active workers after waiting for {} seconds, forcing shutdown.",
                this.activeWorkerCount.get(), DESTROY_MAX_WAIT_SECONDS
            );
        }
    }

    /** 轮询池中可用服务并返回。*/
    private @Nullable ScriptWorker
    pollService() throws InterruptedException
    {
        if (this.isShuttingDown)
        {
            log.warn("Service pool is shutting down, cannot acquire new worker.");
            return null;
        }

        // 调用线程阻塞指定时间，
        // 尝试从阻塞队列里面获取空闲的服务，超时则返回 null
        ScriptWorker worker
            = this.idleWorkerQueue
                  .poll(5L, TimeUnit.SECONDS);

        if (Objects.nonNull(worker)) {
            // 成功分配到服务，活跃计数 + 1
            this.activeWorkerCount.incrementAndGet();
        }

        return worker;
    }

    /**
     * 调用者用完服务后，
     * 将其放回阻塞队列并维护其健康状态。
     *
     * @param worker 使用完成的服务实例（不得为空）
     */
    private void
    returnWorker(@NotNull ScriptWorker worker)
    {
        if (worker.isNotAlive())
        {
            try { worker.restart(); }
            catch (InterruptedException e)
            {
                log.error("Restart failed! This worker will not be re-queued...", e);
                return;
            }
        }

        this.idleWorkerQueue.offer(worker);

        // 归还服务入池，活跃计数 - 1
        this.activeWorkerCount.decrementAndGet();
    }

    /**
     * 开放的执行转换接口 Excel -> Markdown
     *
     * @param tablePath 表格临时文件路径
     *
     * @return 转换完成后的 Markdown 文本
     *
     * @throws ScriptWorkerException 服务启动失败，转换失败最终抛出本异常
     */
    @Override
    public String
    convertTableToMarkdown(String tablePath)
    {
        if (this.isShuttingDown)
        {
            log.warn("Service shutdown in progress - rejecting new request!");
            return null;
        }

        ScriptWorker worker = null;

        try
        {
            worker = this.pollService();

            if (Objects.isNull(worker))
            {
                throw new
                ScriptWorkerException("All service busy! Please try again later...");
            }

            worker.submit(tablePath.trim());
            final String convertMarkdown = worker.getResult();
            worker.checkError();

            return convertMarkdown;
        }
        catch (NotSupportFileExtension notSupport)
        {
            throw new
            ScriptWorkerException(notSupport.getMessage(), notSupport);
        }
        catch (IOException | InterruptedException exception)
        {
            log.error("Exception occurred during communication with python process!", exception);

            if (Objects.nonNull(worker))
            {
                try { worker.restart(); }
                catch (InterruptedException restartException) {
                    log.error("Restart Python service failed!", restartException);
                }
            }

            throw new
            ScriptWorkerException(
                String.format(
                    "Convert excel table to markdown failed! Caused by: %s",
                    exception.getMessage()
                )
            );
        }
        finally
        {
            // 不论失败与否，这个服务实例都要回到阻塞队列
            if (Objects.nonNull(worker)) {
                this.returnWorker(worker);
            }
        }
    }
}