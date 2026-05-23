package com.shutiao.flow

import kotlin.system.exitProcess

class UserService : IUserService.Stub() {

    override fun destroy() = exit()
    override fun exit() = exitProcess(0)

    override fun exec(command: String) {
        // 异步执行：binder 调用立刻返回，子进程的输出被消费避免缓冲区填满导致僵尸进程
        Thread {
            try {
                val process = ProcessBuilder("sh", "-c", command)
                    .redirectErrorStream(true)
                    .start()
                val buf = ByteArray(4096)
                process.inputStream.use { stream ->
                    while (stream.read(buf) != -1) {
                        // discard
                    }
                }
                process.waitFor()
            } catch (_: Exception) {
            }
        }.start()
    }
}
