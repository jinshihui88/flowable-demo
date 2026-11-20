package com.example.flowabledemo.dto;

/** 历史流程变量。value 统一转字符串，避免把引擎内部类型直接序列化。 */
public record HistoricVariableVO(
        String variableName,
        String value,
        String variableTypeName
) {
}
