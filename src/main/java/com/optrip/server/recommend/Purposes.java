package com.optrip.server.recommend;

import java.util.List;

public final class Purposes {

    public static final String MAPPING_VERSION = "v1";

    public static final List<String> ACTIVE = List.of(
            "맛집", "카페투어", "시장/먹거리", "힐링", "자연/풍경",
            "바다", "역사/문화", "문화체험", "액티비티", "하이킹/트레킹");

    private Purposes() {
    }
}
