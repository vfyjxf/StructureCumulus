package dev.vfyjxf.cumulus.uitest;

import java.util.Locale;

/** The meaningful parts a staged structure can expose, with one palette color and description each. */
public enum VisualPartRole {
    controller("controller", "控制器", 0xFFFFC040, "拥有结构集根节点及其上下文"), preheater(
        "preheater",
        "预热器",
        0xFFFF7040,
        "为控制器供热的卫星结构"
    ), port("port", "端口", 0xFF40E0FF, "物品、流体或能量的传输端点"), root("root", "根节点", 0xFFFFFFFF, "层级的根节点"), satellite(
        "satellite",
        "卫星",
        0xFF60E0A0,
        "附着在根节点上的子框架"
    ), subSatellite("subSatellite", "次级卫星", 0xFFC080FF, "嵌套在卫星下的第二级框架"), substructure(
        "substructure",
        "子结构",
        0xFF8080FF,
        "结构集内部的嵌套子结构框架"
    ), member("member", "成员", 0xFFC0C0C0, "结构集中的普通成员"), wall("wall", "炉壁", 0xFF9A9A9A, "围出内部容积的多方块炉壁"), casing(
        "casing",
        "外壳",
        0xFFB0B0B0,
        "围出高炉内部通道的隔热外壳"
    ), window("window", "观察窗", 0xFFB0E0FF, "透明观察窗，可看到高炉内腔"), drain("drain", "排液口", 0xFFFF9040, "排出熔融金属的排液口"), tank(
        "tank",
        "储罐",
        0xFF40B0E0,
        "储存熔融金属或燃料的储罐"
    ), coil("coil", "线圈", 0xFFFF5050, "提供感应加热的线圈层"), hatch(
        "hatch",
        "接口",
        0xFF40E0C0,
        "输入、输出或能量接口"
    ), muffler("muffler", "消音器", 0xFF808080, "排放废气并降低噪音的消音器"), maintenance("maintenance", "维护舱", 0xFF80C040, "维护与检修接口");

    private final String id;
    private final String displayName;
    private final int color;
    private final String description;

    VisualPartRole(String id, String displayName, int color, String description) {
        this.id = id;
        this.displayName = displayName;
        this.color = color;
        this.description = description;
    }

    public String id() {
        return id;
    }

    public String displayName() {
        return displayName;
    }

    public int color() {
        return color;
    }

    public String description() {
        return description;
    }

    public static VisualPartRole forMember(String name, int depth) {
        String lower = name.toLowerCase(Locale.ROOT);
        if (lower.contains("controller")) {
            return controller;
        }
        if (lower.contains("preheater")) {
            return preheater;
        }
        if (lower.contains("port")) {
            return port;
        }
        if (lower.contains("drain")) {
            return drain;
        }
        if (lower.contains("tank")) {
            return tank;
        }
        if (lower.contains("coil")) {
            return coil;
        }
        if (lower.contains("muffler")) {
            return muffler;
        }
        if (lower.contains("maintenance")) {
            return maintenance;
        }
        if (lower.contains("hatch")) {
            return hatch;
        }
        if (lower.contains("wall")) {
            return wall;
        }
        if (lower.contains("sub-satellite") || lower.contains("subsatellite")) {
            return subSatellite;
        }
        if (lower.contains("satellite")) {
            return satellite;
        }
        if (lower.contains("child-frame") || lower.contains("child_frame")) {
            return satellite;
        }
        if (lower.contains("deep-frame")
                || lower.contains("nested-frame")
                || lower.contains("substructure")
                || lower.contains("entity_window")
                || lower.contains("frame")) {
            return substructure;
        }
        if (lower.contains("root")) {
            return root;
        }
        return depth >= 2 ? substructure : member;
    }
}
