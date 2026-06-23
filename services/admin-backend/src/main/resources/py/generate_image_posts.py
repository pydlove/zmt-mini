#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
将 DOCX 文章切分成多张 3:4 贴图卡片（PNG），对齐 AI 创作原型 full-prototype-v20.html。
用法:
    python generate_image_posts.py <docx_path> <output_dir> <title_id> \
        [--style xiaohongshu|wechat|douyin|literary|minimal|business] \
        [--title "封面标题"]
输出 JSON:
    {"success": true, "images": ["/uploads/image-posts/.../xxx_0.png", ...]}
"""

import sys
import os
import json
import re
import time
import argparse
import subprocess
import urllib.request
import math
from docx import Document
from PIL import Image, ImageDraw, ImageFont

# 自动下载的兜底字体缓存路径
_FONT_CACHE_DIR = os.path.join(os.path.expanduser("~"), ".cache", "image_post_fonts")
_DOWNLOAD_FONT_PATH = os.path.join(_FONT_CACHE_DIR, "NotoSansCJKsc-Regular.otf")
_DOWNLOAD_FONT_URL = (
    "https://github.com/googlefonts/noto-cjk/raw/main/"
    "Sans/OTF/SimplifiedChinese/NotoSansCJKsc-Regular.otf"
)

# ============================================================
# 卡片风格配置（与 full-prototype-v20.html 的 cardStyles 保持一致）
# ============================================================
CARD_STYLES = {
    "xiaohongshu": {
        "label": "小红书",
        "accent": "#ff2442",
        "cover_grad": ["#ff2442", "#ff7a8a", "#ffd1d9"],
        "cover_circle": "rgba(255,255,255,0.18)",
        "cover_circle2": "rgba(255,255,255,0.12)",
        "tag_bg": "rgba(255,255,255,0.95)",
        "tag_text": "干货分享",
        "brand_text": "— 爱创作 —",
        "content_bg": "#ffffff",
        "heading_color": "#1a1a1a",
        "body_color": "#595959",
        "num_color": "#ffffff",
        "footer_bg": "#fff0f3",
        "font": "PingFang SC",
    },
    "wechat": {
        "label": "公众号",
        "accent": "#07c160",
        "cover_grad": ["#07c160", "#95de64", "#d9f7be"],
        "cover_circle": "rgba(255,255,255,0.20)",
        "cover_circle2": "rgba(255,255,255,0.14)",
        "tag_bg": "rgba(255,255,255,0.95)",
        "tag_text": "深度好文",
        "brand_text": "— 爱创作 —",
        "content_bg": "#ffffff",
        "heading_color": "#1a1a1a",
        "body_color": "#595959",
        "num_color": "#ffffff",
        "footer_bg": "#f6ffed",
        "font": "PingFang SC",
    },
    "douyin": {
        "label": "抖音",
        "accent": "#25f4ee",
        "cover_grad": ["#0a0a0a", "#1a1a1a", "#fe2c55"],
        "cover_circle": "rgba(37,244,238,0.25)",
        "cover_circle2": "rgba(254,44,85,0.25)",
        "tag_bg": "#25f4ee",
        "tag_text": "上热门",
        "brand_text": "— 爱创作 —",
        "content_bg": "#1a1a1a",
        "heading_color": "#ffffff",
        "body_color": "#d9d9d9",
        "num_color": "#0a0a0a",
        "footer_bg": "#000000",
        "font": "PingFang SC",
    },
    "literary": {
        "label": "文艺",
        "accent": "#8b5e34",
        "cover_grad": ["#8b5e34", "#d4a373", "#f0e6d8"],
        "cover_circle": "rgba(255,255,255,0.18)",
        "cover_circle2": "rgba(255,255,255,0.12)",
        "tag_bg": "rgba(255,255,255,0.92)",
        "tag_text": "慢读时光",
        "brand_text": "— 爱创作 —",
        "content_bg": "#faf5ef",
        "heading_color": "#5a3e2b",
        "body_color": "#8b5e34",
        "num_color": "#ffffff",
        "footer_bg": "#f0e6d8",
        "font": "Georgia",
    },
    "minimal": {
        "label": "极简",
        "accent": "#1a1a1a",
        "cover_grad": ["#1a1a1a", "#262626", "#404040"],
        "cover_circle": "rgba(255,255,255,0.06)",
        "cover_circle2": "rgba(255,255,255,0.04)",
        "tag_bg": "#ffffff",
        "tag_text": "NOTE",
        "brand_text": "— AI CHUANGZUO —",
        "content_bg": "#ffffff",
        "heading_color": "#000000",
        "body_color": "#262626",
        "num_color": "#ffffff",
        "footer_bg": "#fafafa",
        "font": "Helvetica Neue",
    },
    "business": {
        "label": "商务",
        "accent": "#1677ff",
        "cover_grad": ["#003a8c", "#1677ff", "#bae0ff"],
        "cover_circle": "rgba(255,255,255,0.15)",
        "cover_circle2": "rgba(255,255,255,0.10)",
        "tag_bg": "rgba(255,255,255,0.95)",
        "tag_text": "INSIGHT",
        "brand_text": "— AICHUANGZUO —",
        "content_bg": "#ffffff",
        "heading_color": "#003a8c",
        "body_color": "#595959",
        "num_color": "#ffffff",
        "footer_bg": "#f0f5ff",
        "font": "PingFang SC",
    },
}


def _load_truetype(path, size):
    """尝试加载字体，兼容 .ttc / .otf / .ttf / .woff / .woff2"""
    try:
        return ImageFont.truetype(path, size, index=0)
    except Exception:
        return ImageFont.truetype(path, size)


def _discover_fonts_via_fclist():
    """通过 fc-list 动态发现系统中的中文字体"""
    try:
        result = subprocess.run(
            ["fc-list", ":lang=zh", "file"],
            capture_output=True, text=True, timeout=5
        )
        if result.returncode != 0:
            return []
        paths = []
        for line in result.stdout.strip().splitlines():
            line = line.strip()
            if line and line.startswith("/"):
                p = line.split(":")[0].strip()
                if p and os.path.exists(p):
                    paths.append(p)
        return paths
    except Exception:
        return []


def _scan_font_dirs():
    """直接扫描常见字体目录，不依赖 fc-list / fontconfig"""
    dirs = [
        "/usr/share/fonts",
        "/usr/local/share/fonts",
        os.path.expanduser("~/.fonts"),
        "/opt/fonts",
        "/System/Library/Fonts",
        "/Library/Fonts",
        os.path.expanduser("~/Library/Fonts"),
    ]
    exts = {".ttf", ".ttc", ".otf", ".woff", ".woff2"}
    found = []
    for d in dirs:
        if not os.path.isdir(d):
            continue
        for root, _dirs, files in os.walk(d):
            for f in files:
                if os.path.splitext(f)[1].lower() in exts:
                    found.append(os.path.join(root, f))
    prioritized = []
    others = []
    for p in found:
        name = os.path.basename(p).lower()
        if any(k in name for k in ("cjk", "noto", "wqy", "zenhei", "hei", "song", "pingfang")):
            prioritized.append(p)
        else:
            others.append(p)
    return prioritized + others


def _download_fallback_font():
    """自动下载 Noto Sans CJK 作为兜底字体"""
    if os.path.exists(_DOWNLOAD_FONT_PATH):
        return _DOWNLOAD_FONT_PATH
    try:
        os.makedirs(_FONT_CACHE_DIR, exist_ok=True)
        req = urllib.request.Request(
            _DOWNLOAD_FONT_URL,
            headers={"User-Agent": "Mozilla/5.0"}
        )
        with urllib.request.urlopen(req, timeout=60) as resp:
            with open(_DOWNLOAD_FONT_PATH, "wb") as f:
                f.write(resp.read())
        return _DOWNLOAD_FONT_PATH
    except Exception:
        return None


# 用户指定的封面字体路径
# 默认：脚本位于 <project>/services/admin-backend/src/main/resources/py/
# 字体在 <project>/services/admin-frontend/src/assets/font
_CUSTOM_FONT_DIR = os.path.join(
    os.path.dirname(os.path.abspath(__file__)),
    "..", "..", "..", "..", "..", "admin-frontend", "src", "assets", "font"
)

# 全局：通过 --font-family 指定的字体名
_FONT_FAMILY = None


def _try_load_custom_font(size, purpose="cover"):
    """
    尝试加载用户自定义目录中的字体文件。
    purpose: 'cover' 优先加载粗体/书法/艺术字体；'body' 优先加载常规清晰黑体。
    """
    if not os.path.isdir(_CUSTOM_FONT_DIR):
        return None
    exts = {".ttc", ".ttf", ".otf"}
    candidates = []
    for root, _dirs, files in os.walk(_CUSTOM_FONT_DIR):
        for f in files:
            if os.path.splitext(f)[1].lower() in exts:
                candidates.append(os.path.join(root, f))
    candidates.sort(key=lambda p: os.path.basename(p))
    if not candidates:
        return None

    scored = []
    for path in candidates:
        f = os.path.basename(path)
        try:
            font = _load_truetype(path, 40)
            name_tuple = font.getname()
            font_name = " ".join(name_tuple).lower()
            file_name = f.lower()
            combined = font_name + " " + file_name
            s = 0
            is_artistic = any(k in combined for k in ("calligraphy", "brush", "art", "script", "hand", "write"))
            is_calligraphy = any(k in combined for k in ("dao", "li", "mao", "kai", "xingshu", "caoshu", "lishu", "fangyuan"))
            if _FONT_FAMILY:
                target = _FONT_FAMILY.lower()
                dir_name = os.path.basename(os.path.dirname(path)).lower()
                if target in file_name or target in font_name or target in dir_name:
                    s += 1000

            if purpose == "cover":
                if is_calligraphy:
                    s += 20
                if is_artistic:
                    s += 15
                if any(k in combined for k in ("bold", "heavy", "black", "hei", "simhei", "title", "semibold")):
                    s += 8
                if any(k in font_name for k in ("noto", "pingfang", "source han", "microsoft yahei")):
                    s += 2
                if any(k in combined for k in ("regular", "light", "thin", "mono", "code")) and not is_calligraphy and not is_artistic:
                    s -= 5
            else:
                if is_calligraphy or is_artistic:
                    s -= 10
                if any(k in combined for k in ("regular", "normal", "pingfang", "noto", "sans", "hei", "medium", "book", "light", "thin")):
                    s += 10
                if any(k in combined for k in ("bold", "heavy", "black", "title")):
                    s -= 2
            if file_name.endswith(".ttf"):
                s += 2
            scored.append((s, f, path, font_name))
        except Exception:
            continue

    scored.sort(key=lambda x: x[0], reverse=True)
    if scored and _FONT_FAMILY:
        top3 = scored[:3]
        print(f"[font-debug] purpose={purpose} family={_FONT_FAMILY} top3: {[(s, f, n) for s, f, _, n in top3]}", file=sys.stderr)
    for _s, _f, path, _name in scored:
        try:
            font = _load_truetype(path, size)
            print(f"[font-debug] selected {purpose} font: {path} (name={_name})", file=sys.stderr)
            return font
        except Exception:
            continue
    print(f"[font-debug] no custom font matched for purpose={purpose}", file=sys.stderr)
    return None


def find_font(size, prefer_bold=False):
    """跨平台查找中文字体，找不到时自动下载兜底字体或抛出明确错误"""
    candidates = []
    if prefer_bold:
        candidates += [
            "/System/Library/Fonts/PingFang.ttc",
            "/System/Library/Fonts/PingFangSC-Semibold.otf",
            "/System/Library/Fonts/STHeiti Medium.ttc",
            "/usr/share/fonts/truetype/wqy/wqy-zenhei.ttc",
            "/usr/share/fonts/truetype/wqy/wqy-microhei.ttc",
            "/usr/share/fonts/opentype/noto/NotoSansCJK-Bold.ttc",
            "/usr/share/fonts/truetype/noto/NotoSansCJK-Bold.ttc",
            "/usr/share/fonts/google-noto-cjk/NotoSansCJK-Bold.ttc",
            "/usr/share/fonts/opentype/noto/NotoSansCJKsc-Bold.otf",
            "/usr/share/fonts/truetype/dejavu/DejaVuSans-Bold.ttf",
        ]
    else:
        candidates += [
            "/System/Library/Fonts/PingFang.ttc",
            "/System/Library/Fonts/PingFangSC-Regular.otf",
            "/System/Library/Fonts/STHeiti Light.ttc",
            "/usr/share/fonts/truetype/wqy/wqy-zenhei.ttc",
            "/usr/share/fonts/truetype/wqy/wqy-microhei.ttc",
            "/usr/share/fonts/opentype/noto/NotoSansCJK-Regular.ttc",
            "/usr/share/fonts/truetype/noto/NotoSansCJK-Regular.ttc",
            "/usr/share/fonts/google-noto-cjk/NotoSansCJK-Regular.ttc",
            "/usr/share/fonts/opentype/noto/NotoSansCJKsc-Regular.otf",
            "/usr/share/fonts/truetype/dejavu/DejaVuSans.ttf",
        ]

    for path in candidates:
        if os.path.exists(path):
            try:
                return _load_truetype(path, size)
            except Exception:
                continue

    for path in _discover_fonts_via_fclist():
        try:
            return _load_truetype(path, size)
        except Exception:
            continue

    for path in _scan_font_dirs():
        try:
            return _load_truetype(path, size)
        except Exception:
            continue

    downloaded = _download_fallback_font()
    if downloaded:
        try:
            return _load_truetype(downloaded, size)
        except Exception:
            pass

    raise RuntimeError(
        "未找到可用的中文字体。请在服务器执行以下命令安装字体：\n"
        "  Ubuntu/Debian: sudo apt-get install -y fonts-noto-cjk\n"
        "  CentOS/RHEL  : sudo yum install -y google-noto-sans-cjk-ttc-fonts\n"
        "  或手动下载   : wget -O /usr/share/fonts/NotoSansCJKsc-Regular.otf "
        "https://github.com/googlefonts/noto-cjk/raw/main/Sans/OTF/SimplifiedChinese/NotoSansCJKsc-Regular.otf"
    )


def find_cover_font(size):
    """查找封面字体：优先用户自定义粗体/艺术字体，回退系统粗黑体"""
    custom = _try_load_custom_font(size, purpose="cover")
    if custom:
        return custom
    return find_font(size, prefer_bold=True)


def find_body_font(size):
    """查找正文字体：优先用户自定义清晰黑体，回退系统常规黑体"""
    custom = _try_load_custom_font(size, purpose="body")
    if custom:
        return custom
    return find_font(size, prefer_bold=False)


def hex_to_rgb(hex_color):
    hex_color = hex_color.lstrip("#")
    if len(hex_color) == 3:
        hex_color = "".join([c * 2 for c in hex_color])
    return tuple(int(hex_color[i:i + 2], 16) for i in (0, 2, 4))


def rgba_to_rgb(rgba_str):
    """解析 rgba(...) / rgb(...) 字符串为 RGBA 元组"""
    m = re.search(r"rgba?\((\d+),\s*(\d+),\s*(\d+)(?:,\s*([\d.]+))?\)", rgba_str)
    if not m:
        return (255, 255, 255, 255)
    r, g, b = int(m.group(1)), int(m.group(2)), int(m.group(3))
    a = float(m.group(4)) if m.group(4) else 1.0
    return (r, g, b, int(a * 255))


def parse_color(color):
    """统一解析 hex / rgb / rgba"""
    color = color.strip()
    if color.startswith("#"):
        return hex_to_rgb(color) + (255,)
    if color.startswith("rgb"):
        return rgba_to_rgb(color)
    return hex_to_rgb(color) + (255,)


def draw_gradient(draw, width, height, colors, direction="diagonal"):
    """绘制线性渐变背景"""
    if len(colors) == 2:
        c1, c2 = parse_color(colors[0]), parse_color(colors[1])
    elif len(colors) >= 3:
        c1, c2, c3 = parse_color(colors[0]), parse_color(colors[1]), parse_color(colors[2])
    else:
        c1 = c2 = parse_color(colors[0]) if colors else (255, 255, 255, 255)
        c3 = c2

    for y in range(height):
        for x in range(width):
            if direction == "diagonal":
                ratio = (x + y) / max(width + height - 2, 1)
            else:
                ratio = y / max(height - 1, 1)
            if ratio <= 0.5:
                t = ratio * 2
                r = int(c1[0] + (c2[0] - c1[0]) * t)
                g = int(c1[1] + (c2[1] - c1[1]) * t)
                b = int(c1[2] + (c2[2] - c1[2]) * t)
            else:
                t = (ratio - 0.5) * 2
                r = int(c2[0] + (c3[0] - c2[0]) * t)
                g = int(c2[1] + (c3[1] - c2[1]) * t)
                b = int(c2[2] + (c3[2] - c2[2]) * t)
            draw.point((x, y), fill=(r, g, b))


def draw_circle(draw, x, y, radius, color):
    """绘制实心圆；支持 RGBA 半透明（在 RGBA 画布上生效）"""
    r, g, b, a = parse_color(color)
    if a < 255:
        # PIL ImageDraw 不自动做 alpha 合成，手动按 alpha 混合当前像素
        img = draw.im
        width, height = img.size
        x0, y0 = int(x - radius), int(y - radius)
        x1, y1 = int(x + radius), int(y + radius)
        r_f, g_f, b_f, a_f = r, g, b, a / 255.0
        for py in range(max(0, y0), min(height, y1 + 1)):
            for px in range(max(0, x0), min(width, x1 + 1)):
                dx, dy = px - x, py - y
                if dx * dx + dy * dy <= radius * radius:
                    orig = img.getpixel((px, py))
                    if len(orig) == 4:
                        or_, og, ob, oa = orig
                    else:
                        or_, og, ob = orig
                        oa = 255
                    nr = int(or_ * (1 - a_f) + r_f * a_f)
                    ng = int(og * (1 - a_f) + g_f * a_f)
                    nb = int(ob * (1 - a_f) + b_f * a_f)
                    na = int(oa * (1 - a_f) + 255 * a_f)
                    img.putpixel((px, py), (nr, ng, nb, na))
    else:
        draw.ellipse([(x - radius, y - radius), (x + radius, y + radius)], fill=(r, g, b))


def draw_rounded_tag(draw, x, y, width, height, radius, bg_color):
    """绘制圆角矩形标签背景"""
    r, g, b, a = parse_color(bg_color)
    draw.rounded_rectangle([(x, y), (x + width, y + height)], radius=radius, fill=(r, g, b))


def text_size(draw, text, font):
    bbox = draw.textbbox((0, 0), text, font=font)
    return bbox[2] - bbox[0], bbox[3] - bbox[1]


def wrap_text(draw, text, font, max_width, max_lines=None):
    """按像素宽度自动换行，超出 max_lines 时末行截断加 ..."""
    if not text:
        return []
    lines = []
    for paragraph in text.split("\n"):
        paragraph = paragraph.strip()
        if not paragraph:
            continue
        current_line = ""
        for char in paragraph:
            test_line = current_line + char
            w, _ = text_size(draw, test_line, font)
            if w > max_width:
                if current_line:
                    lines.append(current_line)
                current_line = char
            else:
                current_line = test_line
        if current_line:
            lines.append(current_line)

    if max_lines and len(lines) > max_lines:
        lines = lines[:max_lines]
        last = lines[-1]
        while text_size(draw, last + "...", font)[0] > max_width and last:
            last = last[:-1]
        lines[-1] = last + "..."
    return lines


def extract_docx_content(docx_path):
    """提取 DOCX 正文段落，并识别小标题（Heading 2+ 或类似样式）"""
    doc = Document(docx_path)
    title = ""
    paragraphs = []
    headings = []  # (index_in_paragraphs, text)

    heading_pattern = re.compile(r"^(\d+[、.．\s]|第[一二三四五六七八九十]+章|【.*】|\d+\s*[.|、])")

    def _is_heading(para, text):
        style_name = (para.style.name or "").lower()
        # Heading 1 视为文章标题，不作为内容小标题
        if style_name == "heading 1":
            return False
        is_heading_style = "heading" in style_name
        is_bold = all(run.bold for run in para.runs if run.text.strip()) and len(para.runs) > 0
        is_short = len(text) <= 80
        looks_like_heading = heading_pattern.match(text) is not None
        return is_heading_style or (is_bold and is_short) or looks_like_heading

    first_text_seen = False
    for para in doc.paragraphs:
        text = para.text.strip()
        if not text:
            continue

        is_heading = _is_heading(para, text)

        # 第一个非空段落：只有看起来不像小节标题时，才视为文章标题
        if not first_text_seen:
            first_text_seen = True
            if not is_heading:
                title = text
                continue

        paragraphs.append(text)
        if is_heading:
            headings.append((len(paragraphs) - 1, text))

    return title, paragraphs, headings


# ============================================================
# 卡片渲染器
# ============================================================
class CardRenderer:
    WIDTH = 750
    HEIGHT = 1000

    # 内容卡片固定正文字号（不随内容长度缩小）
    CONTENT_BODY_FONT_SIZE = 30
    CONTENT_LINE_HEIGHT = 48  # 字号 + 行间距

    def __init__(self, style="xiaohongshu", brand_text=None, tag_text=None):
        self.style_name = style if style in CARD_STYLES else "xiaohongshu"
        self.style = CARD_STYLES[self.style_name]
        self.font_family = self.style.get("font", "PingFang SC")
        # 品牌文案：外部传入优先，否则使用风格默认
        self.brand_text = brand_text if brand_text else self.style.get("brand_text", "")
        # 封面标签文案：外部传入优先，否则使用风格默认
        self.tag_text = tag_text if tag_text else self.style.get("tag_text", "")

        # 字号与原型 HTML 中 px 值保持一致（PIL 中 1px ≈ 1pt）
        self.font_tag = find_cover_font(22)
        self.font_cover_title = find_cover_font(68)
        self.font_cover_desc = find_body_font(26)
        self.font_brand = find_cover_font(26)
        self.font_num = find_cover_font(48)
        self.font_content_title = find_cover_font(46)
        self.font_content_body = find_body_font(28)
        self.font_footer = find_cover_font(22)
        self._font_cache = {}

    def _get_body_font(self, size):
        """缓存不同大小的正文字体，避免重复扫描字体目录"""
        if size not in self._font_cache:
            self._font_cache[size] = find_body_font(size)
        return self._font_cache[size]

    def _luminance(self, color):
        r, g, b, _ = parse_color(color)
        return 0.299 * r + 0.587 * g + 0.114 * b

    def _contrast_text_color(self, accent, bg):
        """根据 accent 与背景亮度返回合适的标签文字颜色"""
        accent_rgb = parse_color(accent)[:3]
        bg_lum = self._luminance(bg)
        accent_lum = self._luminance(accent)
        # 若 accent 与背景都偏亮，使用深色；否则使用 accent 本身
        if bg_lum > 180 and accent_lum > 180:
            return (26, 26, 26)
        return accent_rgb

    def _create_image(self):
        return Image.new("RGB", (self.WIDTH, self.HEIGHT), (255, 255, 255))

    def render_cover(self, title, desc=""):
        """渲染封面卡片"""
        # 使用 RGBA 以支持半透明装饰圆
        img = Image.new("RGBA", (self.WIDTH, self.HEIGHT), (255, 255, 255, 255))
        draw = ImageDraw.Draw(img)
        s = self.style
        w, h = self.WIDTH, self.HEIGHT

        # 渐变背景
        draw_gradient(draw, w, h, s["cover_grad"], direction="diagonal")

        # 装饰圆（半透明）
        draw_circle(draw, w - 80, 120, 160, s["cover_circle"])
        draw_circle(draw, 60, h - 220, 120, s["cover_circle"])
        draw_circle(draw, w - 200, h - 120, 90, s["cover_circle2"])

        # 标签
        tag_text = self.tag_text
        tag_w, tag_h = 160, 44
        tag_x, tag_y = 60, 80
        draw_rounded_tag(draw, tag_x, tag_y, tag_w, tag_h, 22, s["tag_bg"])
        tw, th = text_size(draw, tag_text, self.font_tag)
        # 标签文字颜色：优先使用 accent；若 accent 与 tagBg 都偏亮，则使用深色保证可读性
        tag_text_color = self._contrast_text_color(s["accent"], s["tag_bg"])
        draw.text((tag_x + (tag_w - tw) // 2, tag_y + (tag_h - th) // 2 - 2),
                  tag_text, font=self.font_tag, fill=tag_text_color)

        # 标题
        title_lines = wrap_text(draw, title, self.font_cover_title, w - 120, max_lines=4)
        y = 320
        for line in title_lines:
            draw.text((60, y), line, font=self.font_cover_title, fill=(255, 255, 255))
            y += 78

        # 摘要
        desc_text = desc[:80] if desc else ""
        desc_lines = wrap_text(draw, desc_text, self.font_cover_desc, w - 120, max_lines=2)
        desc_y = h - 220
        for line in desc_lines:
            draw.text((60, desc_y), line, font=self.font_cover_desc, fill=(255, 255, 255))
            desc_y += 40

        # 品牌文案
        brand = self.brand_text
        draw.text((60, h - 80), brand, font=self.font_brand, fill=(255, 255, 255))

        # 转回 RGB 保存
        return img.convert("RGB")

    def render_content(self, num, title, lines):
        """渲染内容卡片。title 为空时只显示序号圆标，不渲染标题和下划线"""
        img = self._create_image()
        draw = ImageDraw.Draw(img)
        s = self.style
        w, h = self.WIDTH, self.HEIGHT

        has_title = bool(title and title.strip())

        # 背景
        bg = parse_color(s["content_bg"])[:3]
        draw.rectangle([(0, 0), (w, h)], fill=bg)

        # 顶部色条
        accent = parse_color(s["accent"])[:3]
        draw.rectangle([(0, 0), (w, 14)], fill=accent)

        # 序号圆标
        draw.ellipse([(110 - 56, 140 - 56), (110 + 56, 140 + 56)], fill=accent)
        num_str = str(num).zfill(2)
        tw, th = text_size(draw, num_str, self.font_num)
        draw.text((110 - tw // 2, 140 - th // 2 - 2), num_str,
                  font=self.font_num, fill=parse_color(s["num_color"])[:3])

        # 标题与下划线（仅首张内容卡片）
        if has_title:
            title_lines = wrap_text(draw, title, self.font_content_title, w - 120, max_lines=2)
            y = 260
            for line in title_lines:
                draw.text((60, y), line, font=self.font_content_title, fill=parse_color(s["heading_color"])[:3])
                y += 60
            title_end_y = y
            draw.rectangle([(60, title_end_y + 8), (60 + 80, title_end_y + 13)], fill=accent)
            content_y = title_end_y + 60
        else:
            content_y = 280

        # 正文：固定字号，按卡片容量换行展示
        footer_top = h - 110
        available_height = footer_top - content_y - 30
        body_color = parse_color(s["body_color"])[:3]

        body_font = self._get_body_font(self.CONTENT_BODY_FONT_SIZE)
        line_height = self.CONTENT_LINE_HEIGHT
        max_lines = max(4, available_height // line_height)

        full_text = "\n".join(line.strip() for line in lines if line and line.strip())
        wrapped_lines = wrap_text(draw, full_text, body_font, w - 120, max_lines=max_lines)

        y = content_y
        for line in wrapped_lines:
            draw.text((60, y), line, font=body_font, fill=body_color)
            y += line_height

        # 底部品牌栏
        footer_bg = parse_color(s["footer_bg"])[:3]
        draw.rectangle([(0, h - 110), (w, h)], fill=footer_bg)
        draw.text((60, h - 55), self.brand_text, font=self.font_footer, fill=accent)

        return img

    def _calc_max_body_lines(self, has_title, draw):
        """计算一张卡片中正文能容纳的最大行数"""
        w, h = self.WIDTH, self.HEIGHT
        if has_title:
            title_lines = wrap_text(draw, "__T__", self.font_content_title, w - 120, max_lines=2)
            content_y = 260 + len(title_lines) * 60 + 60
        else:
            content_y = 280
        available_height = (h - 110) - content_y - 30
        return max(4, available_height // self.CONTENT_LINE_HEIGHT), content_y

    def split_content_pages(self, title, lines, has_title=True):
        """将长内容按固定字号拆分成多页，优先保持段落完整"""
        if not lines:
            return [""]

        w, h = self.WIDTH, self.HEIGHT
        draw = ImageDraw.Draw(Image.new("RGB", (w, h), (255, 255, 255)))
        max_lines, _ = self._calc_max_body_lines(has_title, draw)

        body_font = self._get_body_font(self.CONTENT_BODY_FONT_SIZE)

        # 先按段落换行，记录每个段落的行数
        paragraph_wrapped = []
        for text in lines:
            text = text.strip()
            if not text:
                continue
            wrapped = wrap_text(draw, text, body_font, w - 120, max_lines=None)
            paragraph_wrapped.append((text, wrapped))

        if not paragraph_wrapped:
            return [""]

        pages = []
        current_lines = []
        current_count = 0

        for text, wrapped in paragraph_wrapped:
            para_line_count = len(wrapped)

            # 单个段落就超过一页：必须拆开
            if para_line_count > max_lines:
                if current_lines:
                    pages.append("\n".join(current_lines))
                    current_lines = []
                    current_count = 0
                for i in range(0, para_line_count, max_lines):
                    pages.append("\n".join(wrapped[i:i + max_lines]))
                continue

            # 当前页放不下这个段落，先结束当前页
            if current_count + para_line_count > max_lines and current_lines:
                pages.append("\n".join(current_lines))
                current_lines = []
                current_count = 0

            current_lines.extend(wrapped)
            current_count += para_line_count

        if current_lines:
            pages.append("\n".join(current_lines))

        return pages if pages else [""]


# ============================================================
# 主生成逻辑
# ============================================================
def build_cards(title, paragraphs, headings):
    """根据标题和段落构建卡片数据结构"""
    heading_idx_set = set(p_idx for p_idx, _ in headings)
    # 封面摘要：优先取第一个非小标题段落
    desc = ""
    for idx, p in enumerate(paragraphs):
        if idx not in heading_idx_set:
            desc = p
            break
    if not desc and paragraphs:
        desc = paragraphs[0]

    cards = [{"type": "cover", "title": title or "未命名文章", "desc": desc}]

    if not headings:
        # 没有小标题：把所有段落放到一个内容卡片，由渲染时自动分页
        if paragraphs:
            cards.append({
                "type": "content",
                "num": 1,
                "title": "",
                "content": paragraphs
            })
        return cards

    for idx, (p_idx, heading_text) in enumerate(headings):
        content_parts = []
        # 收集当前小标题后到下一个标题前的段落（保留完整内容，由渲染时自动排版）
        start = p_idx + 1
        end = headings[idx + 1][0] if idx + 1 < len(headings) else len(paragraphs)
        for p in paragraphs[start:end]:
            content_parts.append(p)
        cards.append({
            "type": "content",
            "num": idx + 1,
            "title": heading_text,
            "content": content_parts
        })
    return cards


def generate(docx_path, output_dir, title_id, style="xiaohongshu", title="", brand_text=None, tag_text=None):
    """主入口：生成全部贴图"""
    docx_title, paragraphs, headings = extract_docx_content(docx_path)
    if not title:
        title = docx_title if docx_title else "文章"

    os.makedirs(output_dir, exist_ok=True)

    # 清理旧 PNG（保持旧行为）
    for f in os.listdir(output_dir):
        if f.endswith(".png"):
            os.remove(os.path.join(output_dir, f))

    renderer = CardRenderer(style=style, brand_text=brand_text, tag_text=tag_text)
    cards = build_cards(title, paragraphs, headings)

    timestamp = str(int(time.time()))
    image_paths = []
    card_num = 1
    file_index = 1
    for card in cards:
        if card["type"] == "cover":
            img = renderer.render_cover(card["title"], card.get("desc", ""))
            filename = f"{file_index}-{title_id}-{timestamp}.png"
            path = os.path.join(output_dir, filename)
            img.save(path, "PNG")
            rel_path = "/uploads/image-posts/" + os.path.basename(output_dir) + "/" + filename
            image_paths.append(rel_path)
            file_index += 1
        else:
            section_title = card["title"]
            pages = renderer.split_content_pages(section_title, card.get("content", []))
            # 第一页带标题，后续页只保留序号圆标不重复标题
            first_page = True
            for page_text in pages:
                page_title = section_title if first_page else ""
                img = renderer.render_content(card_num, page_title, [page_text])
                filename = f"{file_index}-{title_id}-{timestamp}.png"
                path = os.path.join(output_dir, filename)
                img.save(path, "PNG")
                rel_path = "/uploads/image-posts/" + os.path.basename(output_dir) + "/" + filename
                image_paths.append(rel_path)
                file_index += 1
                card_num += 1
                first_page = False

    return image_paths


def main():
    parser = argparse.ArgumentParser(description="Generate 3:4 image-post cards from DOCX")
    parser.add_argument("docx_path", help="Path to input DOCX file")
    parser.add_argument("output_dir", help="Directory to save PNG files")
    parser.add_argument("title_id", help="Title ID for file naming")
    parser.add_argument("--title", default="", help="封面标题（优先使用，不传入则从 DOCX 提取）")
    parser.add_argument("--style", default="xiaohongshu",
                        choices=list(CARD_STYLES.keys()),
                        help="卡片风格")
    parser.add_argument("--brand-text", default="",
                        help="封面与内容卡底部品牌文案（如作者名称），不传则使用风格默认")
    parser.add_argument("--tag-text", default="",
                        help="封面左上角标签文案（如赛道名称），不传则使用风格默认")
    parser.add_argument("--font-dir", default="",
                        help="自定义字体目录绝对路径（覆盖默认相对路径）")
    parser.add_argument("--font-family", default="",
                        help="指定字体名（文件名或字体名子串）")

    args = parser.parse_args()

    if args.font_dir:
        global _CUSTOM_FONT_DIR
        _CUSTOM_FONT_DIR = args.font_dir

    if args.font_family:
        global _FONT_FAMILY
        _FONT_FAMILY = args.font_family

    if not os.path.exists(args.docx_path):
        print(json.dumps({"success": False, "error": "DOCX file not found"}, ensure_ascii=False))
        sys.exit(1)

    try:
        images = generate(args.docx_path, args.output_dir, args.title_id,
                          style=args.style, title=args.title, brand_text=args.brand_text, tag_text=args.tag_text)
        print(json.dumps({"success": True, "images": images}, ensure_ascii=False))
    except Exception as e:
        print(json.dumps({"success": False, "error": str(e)}, ensure_ascii=False))
        sys.exit(1)


if __name__ == "__main__":
    main()
