package com.example.gsb.perfreg;

/** 判定结果。 */
public enum Verdict {
    /** 首次运行，已建立基线。 */
    BASELINE_CREATED,
    /** 与基线相比无显著退化。 */
    NO_REGRESSION,
    /** 显著退化且越过阈值，判定为回归。 */
    REGRESSION
}
