package com.shutiao.flow

import kotlin.system.exitProcess

class UserService : IUserService.Stub() {

    override fun destroy() = exit()
    override fun exit() = exitProcess(0)
    override fun exec(command: String): String? {
        Runtime.getRuntime().exec(arrayOf("sh", "-c", command))
        return null
    }
}
