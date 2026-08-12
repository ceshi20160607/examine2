package com.unique.examine.core.id;

import com.baomidou.mybatisplus.core.incrementer.DefaultIdentifierGenerator;
import org.springframework.stereotype.Component;

@Component
public class IdService {
    private final DefaultIdentifierGenerator generator = DefaultIdentifierGenerator.getInstance();

    public long nextId() {
        return generator.nextId(null).longValue();
    }
}
