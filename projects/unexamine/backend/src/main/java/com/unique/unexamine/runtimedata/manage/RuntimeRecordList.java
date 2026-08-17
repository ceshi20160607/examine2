package com.unique.unexamine.runtimedata.manage;

import java.util.List;

public record RuntimeRecordList(List<RuntimeRecordView> records, long total, int page, int pageSize) {
}
