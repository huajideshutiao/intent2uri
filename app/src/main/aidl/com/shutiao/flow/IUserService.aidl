// IUserService.aidl
package com.shutiao.flow;

interface IUserService {
    void destroy() = 16777114;
    void exit() = 1;
    String exec(String command) = 100001;
}
