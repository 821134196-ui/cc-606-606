package com.community.medbox.service;

import jakarta.enterprise.context.ApplicationScoped;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 本地模拟扫码/条码：现场扫描袋码、封条码、接收人签收码均由本服务生成，
 * 不依赖任何外部扫码硬件或第三方接口。
 */
@ApplicationScoped
public class ScanService {

    private static final DateTimeFormatter FMT = DateTimeFormatter.ofPattern("yyyyMMddHHmmss");
    private final AtomicInteger seq = new AtomicInteger(1000);

    /** 生成袋号：B-日期-序号 */
    public String nextBagNo() {
        return "B-" + LocalDateTime.now().format(FMT) + "-" + seq.incrementAndGet();
    }

    /** 模拟扫码：返回一个"扫描到"的封条/签收码 */
    public String simulateScan(String prefix) {
        return prefix + "-" + System.nanoTime() % 1_000_000L;
    }
}
