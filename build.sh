#!/system/bin/sh
#=============================================
# Android Code Studio (ACS Lite) 通用编译脚本
# 适配 com.nullij.androidcodestudio
#
# ACS Lite 的 JDK(Temurin 21)/aapt2 均为 glibc 版,
# 必须通过 proot 进入 Ubuntu rootfs 运行 gradle。
#
# 用法:
#   sh build.sh              → 交互菜单
#   sh build.sh debug        → 切换到 debug
#   sh build.sh release      → 切换到 release
#   sh build.sh build        → 编译+安装（当前变体）
#   sh build.sh debug build  → 切换 debug 并编译
#   sh build.sh clean        → 清理构建
#   sh build.sh status       → 查看状态
#=============================================

set -e

# ============ Root 检测 ============
IS_ROOT=false
[ "$(id -u)" = "0" ] && IS_ROOT=true
as_root() {
    if $IS_ROOT; then
        eval "$@"
    else
        su -c "$@"
    fi
}

# ============ 路径配置 ============
# Android Code Studio Lite (com.nullij.androidcodestudio)
ACS_USER="com.nullij.androidcodestudio"
ACS_ROOT="/data/user/0/${ACS_USER}/files"
ACS_HOME="${ACS_ROOT}/home"

# PRoot + Ubuntu rootfs (JDK/aapt2 都在里面, glibc)
PROOT_BIN="${ACS_ROOT}/localenv/bin/proot"
ROOTFS="${ACS_ROOT}/localenv/acsenv"

# Android SDK
ANDROID_HOME="${ACS_HOME}/Android/Sdk"
export ANDROID_HOME
export ANDROID_SDK_ROOT="${ANDROID_HOME}"

# JDK - 容器内路径 (Temurin 21)
JAVA_HOME_CONTAINER="/opt/temurin/jdk-21"

# AAPT2 - 容器内路径 (glibc 版, 只能容器内跑)
AAPT2_BIN_CONTAINER="/home/aapt/aapt2"

# Gradle 用户目录 (宿主机路径, 会 bind 到容器 /home/.gradle)
GRADLE_USER_HOME="${ACS_HOME}/.gradle"
export GRADLE_USER_HOME

# 变体状态文件 (ACS Lite 没有 ProjectService.xml, 自己存)
VARIANT_FILE="${ACS_HOME}/.build_variant"

PROJECT_DIR="$(cd "$(dirname "$0")" && pwd)"

cd "$PROJECT_DIR" || exit 1

# ============ 颜色 ============
RED='\033[0;31m'; GREEN='\033[0;32m'; YELLOW='\033[1;33m'; CYAN='\033[0;36m'; BLUE='\033[0;34m'; NC='\033[0m'
info()  { printf "${GREEN}%s${NC}\n" "$1"; }
warn()  { printf "${YELLOW}%s${NC}\n" "$1"; }
err()   { printf "${RED}%s${NC}\n" "$1"; exit 1; }
header(){ printf "${CYAN}%s${NC}\n" "$1"; }
dim()   { printf "\033[2m%s\033[0m\n" "$1"; }

# ============ 容器内执行命令 ============
# 用法: in_container <命令...>
# 通过 proot 进入 Ubuntu rootfs 执行, 绑定:
#   /dev /proc /sys, ${ACS_HOME} -> /home, /storage -> /storage
#   项目在 Termux home 下时额外绑定 (容器内同路径可访问)
in_container() {
    local cmd="$*"
    local proot_cmd
    local bind_home=""
    if echo "$PROJECT_DIR" | grep -q "^/data/user/0/com.termux/files/home"; then
        bind_home="-b /data/user/0/com.termux/files/home:/data/user/0/com.termux/files/home"
    fi
    proot_cmd="export JAVA_HOME=${JAVA_HOME_CONTAINER}; "
    proot_cmd="${proot_cmd}export ANDROID_HOME=/home/Android/Sdk; "
    proot_cmd="${proot_cmd}export ANDROID_SDK_ROOT=/home/Android/Sdk; "
    proot_cmd="${proot_cmd}export GRADLE_USER_HOME=/home/.gradle; "
    proot_cmd="${proot_cmd}export LANG=C.UTF-8 LC_ALL=C.UTF-8; "
    proot_cmd="${proot_cmd}export PATH=${JAVA_HOME_CONTAINER}/bin:/usr/bin:/bin; "
    proot_cmd="${proot_cmd}cd '${PROJECT_DIR}' && ${cmd}"
    as_root "'${PROOT_BIN}' -r '${ROOTFS}' -b /dev -b /proc -b /sys -b '${ACS_HOME}:/home' -b /storage ${bind_home} -w /root /bin/bash -c \"${proot_cmd}\""
}

# ============ 检测环境 ============
check_env() {
    local issues=0

    # PRoot
    if as_root "test -x '$PROOT_BIN'" 2>/dev/null; then
        dim "  PRoot: $PROOT_BIN"
    else
        warn "  ⚠ 未找到 PRoot: $PROOT_BIN"
        issues=$((issues + 1))
    fi

    # Rootfs
    if as_root "test -d '$ROOTFS'" 2>/dev/null; then
        dim "  Rootfs: Ubuntu (ACS Lite)"
    else
        warn "  ⚠ 未找到 rootfs: $ROOTFS"
        issues=$((issues + 1))
    fi

    # JDK (容器内)
    if as_root "test -x '$ROOTFS/opt/temurin/jdk-21/bin/java'" 2>/dev/null; then
        dim "  JDK: Temurin 21 (容器内)"
    else
        warn "  ⚠ 未找到容器内 JDK: /opt/temurin/jdk-21"
        issues=$((issues + 1))
    fi

    # Android SDK
    if as_root "test -d '$ANDROID_HOME'" 2>/dev/null; then
        dim "  SDK: $ANDROID_HOME"
    else
        warn "  ⚠ 未找到 SDK: $ANDROID_HOME"
        issues=$((issues + 1))
    fi

    # aapt2
    if as_root "test -f '$ACS_HOME/aapt/aapt2'" 2>/dev/null; then
        dim "  aapt2: $ACS_HOME/aapt/aapt2"
    else
        warn "  ⚠ 未找到 aapt2: $ACS_HOME/aapt/aapt2"
        issues=$((issues + 1))
    fi

    # 检查 gradlew
    if [ ! -f "$PROJECT_DIR/gradlew" ]; then
        warn "  ⚠ 未找到 gradlew"
        issues=$((issues + 1))
    fi

    if [ $issues -gt 0 ]; then
        echo ""
        warn "  有 $issues 个问题，编译可能失败"
    fi
}

# ============ 读取当前变体 ============
read_variant() {
    local v=""
    if [ -f "$VARIANT_FILE" ]; then
        v=$(as_root "cat '$VARIANT_FILE' 2>/dev/null" 2>/dev/null | tr -d '\r\n')
    fi
    case "$v" in
        debug|release) ;;
        *) v="debug" ;;
    esac
    echo "$v"
}

# ============ 设置变体 ============
set_variant() {
    local v="$1"
    as_root "echo '$v' > '$VARIANT_FILE'" 2>/dev/null || warn "  ⚠ 写入变体文件失败"
    info "  ✅ 已切换变体: $v"
}

# ============ 配置 aapt2 (写入 gradle.properties, 容器内路径) ============
configure_aapt2() {
    local arch=$(uname -m)
    if [ "$arch" != "aarch64" ] && [ "$arch" != "arm64" ]; then
        warn "  非 ARM64 架构，跳过 AAPT2 配置"
        return 0
    fi

    if ! as_root "test -f '$ACS_HOME/aapt/aapt2'" 2>/dev/null; then
        warn "  未找到 ACS Lite 的 AAPT2: $ACS_HOME/aapt/aapt2"
        return 1
    fi

    # 写入 gradle.properties - 路径用容器内视角 (/home/aapt/aapt2)
    local gradle_props="$PROJECT_DIR/gradle.properties"
    if [ -f "$gradle_props" ]; then
        cp "$gradle_props" "$gradle_props.bak" 2>/dev/null || true
    fi

    sed -i '/android\.aapt2FromMavenOverride/d' "$gradle_props" 2>/dev/null || true
    echo "android.aapt2FromMavenOverride=${AAPT2_BIN_CONTAINER}" >> "$gradle_props"
    dim "  aapt2 -> ${AAPT2_BIN_CONTAINER} (容器内)"
}

# ============ 修复 gradle.properties 属性拼接 ============
fix_gradle_properties() {
    local gradle_props="$PROJECT_DIR/gradle.properties"
    if [ ! -f "$gradle_props" ]; then
        return 0
    fi

    if grep -qE '=(true|false)[a-zA-Z]' "$gradle_props"; then
        warn "  检测到 gradle.properties 属性拼接错误，正在修复..."
        sed -i 's/\(=true\)\([a-zA-Z]\)/\1\n\2/g' "$gradle_props"
        sed -i 's/\(=false\)\([a-zA-Z]\)/\1\n\2/g' "$gradle_props"
        info "  gradle.properties 修复完成"
    fi
}

# ============ 获取任务名 ============
get_task() {
    case "$1" in
        debug)      echo "assembleDebug" ;;
        release)    echo "assembleRelease" ;;
        *)          echo "assembleDebug" ;;
    esac
}

# ============ 编译 ============
build_app() {
    local variant="$1"
    [ -z "$variant" ] && variant=$(read_variant)
    local gradle_task=$(get_task "$variant")

    configure_aapt2
    fix_gradle_properties

    header "═══════════════════════════════════════"
    info "  编译: $gradle_task ($variant)"
    header "═══════════════════════════════════════"

    export TERM=dumb

    # ACS Lite 的 init-scripts: 强制 buildToolsVersion=36.1.0 / ndkVersion
    # (IDE 内部就是显式传 --init-script, 不传则 AGP 默认找 build-tools 35.0.0)
    local INIT_SCRIPTS=""
    if as_root "test -f '$ACS_HOME/.gradle/init-scripts/force-build-tools.gradle'" 2>/dev/null; then
        INIT_SCRIPTS="-I /home/.gradle/init-scripts/force-build-tools.gradle -I /home/.gradle/init-scripts/force-cmake.gradle"
    fi

    # storage 是 fuse 无执行权限, 容器内用 sh 调 gradlew
    in_container "sh gradlew $INIT_SCRIPTS --no-daemon --console=plain -Dsun.stdout.encoding=UTF-8 -Dsun.stderr.encoding=UTF-8 '$gradle_task' 2>&1" \
        || err "  ❌ 编译失败"

    info "  ✅ 编译成功"

    # 查找APK
    header "═══════════════════════════════════════"
    info "  查找 APK..."
    header "═══════════════════════════════════════"

    local apk_path=""
    if [ "$variant" = "debug" ]; then
        apk_path="$PROJECT_DIR/app/build/outputs/apk/debug/app-debug.apk"
    else
        if [ -f "$PROJECT_DIR/app/build/outputs/apk/release/app-release.apk" ]; then
            apk_path="$PROJECT_DIR/app/build/outputs/apk/release/app-release.apk"
        elif [ -f "$PROJECT_DIR/app/build/outputs/apk/release/app-release-unsigned.apk" ]; then
            apk_path="$PROJECT_DIR/app/build/outputs/apk/release/app-release-unsigned.apk"
        fi
    fi

    if [ -z "$apk_path" ] || [ ! -f "$apk_path" ]; then
        apk_path=$(find "$PROJECT_DIR/app/build/outputs/apk" -name "*.apk" 2>/dev/null | sort | tail -1)
    fi

    if [ -n "$apk_path" ] && [ -f "$apk_path" ]; then
        local apk_size=$(du -h "$apk_path" | cut -f1)
        info "  APK: $apk_path ($apk_size)"

        # 复制到 output 目录
        local output_dir="$PROJECT_DIR/output"
        mkdir -p "$output_dir"
        local timestamp=$(date +"%Y%m%d_%H%M%S")
        local project_name=$(basename "$PROJECT_DIR")
        local output_name="${project_name}_${variant}_${timestamp}.apk"
        cp "$apk_path" "$output_dir/$output_name"
        info "  已复制到: $output_dir/$output_name"

        # 安装 - 使用 root权限通过 pm 安装 APK
        header "═══════════════════════════════════════"
        info "  安装 APK..."
        header "═══════════════════════════════════════"

        local apk_path_tmp="$apk_path"
        local install_cmd=""

        # 对于 storage/emulated 或 Termux home 上的文件, 需要复制到 /data/local/tmp 并 root 安装
        if echo "$apk_path" | grep -qE "^/storage/emulated/|^/data/user/0/|^/data/data/"; then
            local tmp="/data/local/tmp/$(basename "$apk_path")"
            su -c "cp '$apk_path_tmp' '$tmp'" 2>/dev/null
            if [ -f "$tmp" ]; then
                install_cmd="su -c 'pm install -r $tmp'"
            else
                warn "  ⚠ 复制 APK 到 /data/local/tmp 失败"
            fi
        else
            if command -v pm >/dev/null 2>&1; then
                install_cmd="pm install -r '$apk_path_tmp'"
            elif command -v cmd >/dev/null 2>&1; then
                install_cmd="cmd package install -r '$apk_path_tmp'"
            else
                warn "  ⚠ 未找到安装命令"
                install_cmd=""
            fi
        fi

        if [ -n "$install_cmd" ]; then
            eval "$install_cmd" 2>&1 && info "  ✅ 安装成功！" || warn "  ⚠ 安装失败"
        fi
    else
        warn "  ⚠ 未找到生成的 APK"
    fi

    header "═══════════════════════════════════════"
    info "  ✅ 完成！"
    header "═══════════════════════════════════════"
}

# ============ 清理 ============
clean_build() {
    header "═══════════════════════════════════════"
    info "  清理构建"
    header "═══════════════════════════════════════"
    local INIT_SCRIPTS=""
    if as_root "test -f '$ACS_HOME/.gradle/init-scripts/force-build-tools.gradle'" 2>/dev/null; then
        INIT_SCRIPTS="-I /home/.gradle/init-scripts/force-build-tools.gradle -I /home/.gradle/init-scripts/force-cmake.gradle"
    fi
    in_container "sh gradlew $INIT_SCRIPTS --stop 2>/dev/null || true"
    rm -rf app/build
    info "  ✅ 已清理"
}

# ============ 状态查看 ============
show_status() {
    local v=$(read_variant)
    local task=$(get_task "$v")
    local apk_size="?"
    local apk_path=$(find "$PROJECT_DIR/app/build/outputs/apk" -name "*.apk" 2>/dev/null | sort | tail -1)
    [ -n "$apk_path" ] && apk_size=$(ls -lh "$apk_path" | awk '{print $5}')

    echo ""
    header "═══════════════════════════════════════"
    info "  项目: $(basename "$PROJECT_DIR")"
    info "  目录: $PROJECT_DIR"
    echo ""
    info "  ACS:  com.nullij.androidcodestudio (ACS Lite)"
    info "  JDK:  Temurin 21 (proot 容器内)"
    info "  SDK:  ${ANDROID_HOME}"
    if as_root "test -f '$ACS_HOME/aapt/aapt2'" 2>/dev/null; then
        info "  aapt2: ${ACS_HOME}/aapt/aapt2"
    else
        warn "  aapt2: 未找到"
    fi
    echo ""
    info "  构建变体: $v"
    info "  Task: $task"
    echo ""
    info "  APK大小: $apk_size"
    [ -n "$apk_path" ] && dim "  $apk_path"
    header "═══════════════════════════════════════"
}

# ============ 显示菜单 ============
show_menu() {
    local v=$(read_variant)
    echo ""
    header "╔══════════════════════════════════════╗"
    header "║   Android Code Studio 编译工具       ║"
    header "╠══════════════════════════════════════╣"
    printf "║  ${GREEN}当前变体${NC}: %-33s║\n" "$v"
    header "╠══════════════════════════════════════╣"
    echo "║                                        ║"
    echo "║  [1]  编译应用 (debug)                 ║"
    echo "║  [2]  编译应用 (release)               ║"
    echo "║                                        ║"
    echo "║  [3]  清理构建 (Clean)                 ║"
    echo "║  [4]  查看状态                         ║"
    echo "║                                        ║"
    echo "║  [0]  退出                             ║"
    echo "║                                        ║"
    header "╚══════════════════════════════════════╝"
    printf "请输入选项 [0-4]: "
}

# ============ 主逻辑 ============
check_env
configure_aapt2
fix_gradle_properties

if [ $# -eq 0 ]; then
    # 无參数 → 菜单模式
    while true; do
        show_menu
        read choice
        case "$choice" in
            1) set_variant "debug";   build_app "debug" ;;
            2) set_variant "release"; build_app "release" ;;
            3) clean_build ;;
            4) show_status ;;
            0) echo ""; exit 0 ;;
            *) warn "  无效选项，请重新输入" ;;
        esac
        echo ""
        dim "按 Enter 继续..."
        read dummy
    done
else
    # 有參数 → 命令行模式
    case "$1" in
        debug|release)
        V="$1"
        set_variant "$V"
        if [ $# -ge 2 ]; then
            shift
            case "$1" in
                build|b)     build_app "$V" ;;
                clean|c)     clean_build ;;
                status|st)   show_status ;;
                *)           show_status ;;
            esac
        else
            show_status
        fi
        ;;
        build|b)
            build_app "$(read_variant)" ;;
        clean|c)
            clean_build ;;
        status|st)
            show_status ;;
        menu|m)
            while true; do
                show_menu
                read choice
                case "$choice" in
                    1) set_variant "debug";   build_app "debug" ;;
                    2) set_variant "release"; build_app "release" ;;
                    3) clean_build ;;
                    4) show_status ;;
                    0) echo ""; exit 0 ;;
                    *) warn "  无效选项" ;;
                esac
                echo ""
                dim "按 Enter 继续..."
                read dummy
            done
            ;;
        *)
            echo ""
            info "用法: sh build.sh [选项]"
            echo ""
            dim "  ┌─────────────────────────────────────┐"
            dim "  │  变体切换                           │"
            dim "  │    debug       切换到 debug 变体    │"
            dim "  │    release     切换到 release 变体  │"
            dim "  │                                     │"
            dim "  │  操作                               │"
            dim "  │    build / b   编译+安装(当前变体)  │"
            dim "  │    clean / c   清理构建             │"
            dim "  │    status / st 查看状态             │"
            dim "  │    menu / m    交互菜单             │"
            dim "  │                                     │"
            dim "  │  组合: sh build.sh debug build      │"
            dim "  └─────────────────────────────────────┘"
            exit 1 ;;
    esac
fi
