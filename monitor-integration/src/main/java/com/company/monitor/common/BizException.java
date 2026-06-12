package com.company.monitor.common;

import lombok.Getter;

/**
 * 业务异常，code 非 0 表示业务错误。
 */
@Getter
public class BizException extends RuntimeException {

    private final int code;

    public BizException(String message) {
        this(500, message);
    }

    public BizException(int code, String message) {
        super(message);
        this.code = code;
    }
}
