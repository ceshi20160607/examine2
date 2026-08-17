package com.unique.unexamine.authorization.manage;

import java.util.List;

public record DataScopeExpression(String mode, List<DataScopeTerm> terms) {
}
