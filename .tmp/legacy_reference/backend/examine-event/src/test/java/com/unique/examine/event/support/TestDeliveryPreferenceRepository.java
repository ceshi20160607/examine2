package com.unique.examine.event.support;

import com.unique.examine.event.domain.DeliveryPreference;
import com.unique.examine.event.domain.DeliveryChannel;
import com.unique.examine.event.port.DeliveryPreferenceRepository;

import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public final class TestDeliveryPreferenceRepository implements DeliveryPreferenceRepository {
    private final Map<String, DeliveryPreference> values = new LinkedHashMap<>();
    private long nextId = 9000;
    private int writes;

    @Override
    public long nextId() {
        return ++nextId;
    }

    @Override
    public Optional<DeliveryPreference> find(long systemId, long tenantId, long memberId,
                                               String templateCode, DeliveryChannel channel) {
        return Optional.ofNullable(values.get(key(systemId, tenantId, memberId, templateCode, channel)));
    }

    @Override
    public List<DeliveryPreference> findAll(long systemId, long tenantId, long memberId,
                                             DeliveryChannel channel) {
        return values.values().stream()
                .filter(value -> value.systemId() == systemId && value.tenantId() == tenantId
                        && value.memberId() == memberId && value.channel() == channel)
                .sorted(Comparator.comparing(DeliveryPreference::templateCode))
                .toList();
    }

    @Override
    public boolean insert(DeliveryPreference preference) {
        var key = key(preference.systemId(), preference.tenantId(), preference.memberId(),
                preference.templateCode(), preference.channel());
        if (values.containsKey(key)) return false;
        values.put(key, preference);
        writes++;
        return true;
    }

    @Override
    public boolean update(DeliveryPreference preference, long expectedVersion) {
        var key = key(preference.systemId(), preference.tenantId(), preference.memberId(),
                preference.templateCode(), preference.channel());
        var current = values.get(key);
        if (current == null || current.version() != expectedVersion) return false;
        values.put(key, preference);
        writes++;
        return true;
    }

    public int writes() {
        return writes;
    }

    private static String key(long systemId, long tenantId, long memberId, String templateCode,
                              DeliveryChannel channel) {
        return systemId + ":" + tenantId + ":" + memberId + ":" + templateCode + ":" + channel;
    }
}
