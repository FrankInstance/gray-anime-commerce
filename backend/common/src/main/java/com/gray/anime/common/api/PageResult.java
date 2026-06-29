package com.gray.anime.common.api;

import java.util.List;

public record PageResult<T>(List<T> items, long page, long size, long total) {
}
