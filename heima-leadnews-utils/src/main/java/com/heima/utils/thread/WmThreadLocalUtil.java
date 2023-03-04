package com.heima.utils.thread;

import com.heima.model.wemedia.pojos.WmUser;

/**
 * @author WangNing
 */
public class WmThreadLocalUtil {

    private final static ThreadLocal<WmUser> WM_USER_THREAD_LOCAL = new ThreadLocal<>();

    /**
     * 将当前用户存入线程
     *
     * @param wmUser
     */
    public static void setUser(WmUser wmUser) {
        WM_USER_THREAD_LOCAL.set(wmUser);
    }

    /**
     * 从线程中获取当前用户
     */
    public static WmUser getUser() {
        return WM_USER_THREAD_LOCAL.get();
    }

    /**
     * 清除线程中的当前用户id
     */
    public static void clear() {
        WM_USER_THREAD_LOCAL.remove();
    }
}
