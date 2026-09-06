// IUserService.aidl
package com.shutiao.flow;

interface IUserService {
    // transaction 16777114 是 Shizuku 约定的销毁回调，由框架在 unbind 时调用，不可改名或改号
    void destroy() = 16777114;
    void exec(String command) = 100001;
}
