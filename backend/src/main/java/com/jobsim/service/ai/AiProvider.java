package com.jobsim.service.ai;

/**
 * AI 评估服务抽象。新增真实模型服务时实现本接口即可接入，
 * 上层的评估流程（降级、落库、统计）不需要改动。
 */
public interface AiProvider {

    /** 来源标识：MOCK / REMOTE */
    String name();

    EvaluationResult evaluate(EvaluationContext context) throws AiServiceException;
}
