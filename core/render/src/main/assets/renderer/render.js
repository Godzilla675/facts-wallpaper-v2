/*
 * Offline stage renderer bridge for Facts Wallpaper.
 *
 * The Kotlin host calls:
 *   window.renderStudyMarkup(kind, source, fgColorHex, bgColorHex, accentColorHex)
 *
 * The promise resolves with { ok, width, height, error? } and posts the exact same object
 * to window.AndroidRenderBridge.postResult(json) when the host bridge is available. No
 * network APIs are used; every script/font is a local bundled asset.
 */
(function () {
    "use strict";

    var stage = document.getElementById("stage");
    var renderSequence = 0;

    function currentHtml() {
        return document.documentElement.outerHTML;
    }

    function post(payload) {
        try {
            if (window.AndroidRenderBridge && typeof window.AndroidRenderBridge.postResult === "function") {
                window.AndroidRenderBridge.postResult(JSON.stringify(payload));
            }
        } catch (ignore) {
            // The host may already be gone after a timeout; the promise remains harmless.
        }
    }

    function bounds() {
        var rect = stage.getBoundingClientRect();
        return {
            width: Math.max(1, Math.ceil(rect.width)),
            height: Math.max(1, Math.ceil(rect.height))
        };
    }

    function afterPaint() {
        return new Promise(function (resolve) {
            requestAnimationFrame(function () {
                requestAnimationFrame(function () {
                    resolve();
                });
            });
        });
    }

    function waitForFonts() {
        if (document.fonts && document.fonts.ready) {
            return document.fonts.ready.catch(function () { return undefined; });
        }
        return Promise.resolve();
    }

    async function renderLatex(source) {
        if (!window.katex) {
            throw new Error("KaTeX is unavailable");
        }
        stage.innerHTML = katex.renderToString(source, {
            displayMode: true,
            throwOnError: false,
            strict: "ignore",
            trust: false
        });
    }

    async function renderMermaid(source, fg, bg, accent) {
        if (!window.mermaid) {
            throw new Error("Mermaid is unavailable");
        }
        mermaid.initialize({
            startOnLoad: false,
            securityLevel: "strict",
            theme: "base",
            fontFamily: "sans-serif",
            themeVariables: {
                background: bg,
                primaryColor: accent,
                primaryTextColor: fg,
                primaryBorderColor: accent,
                secondaryColor: bg,
                tertiaryColor: bg,
                lineColor: fg,
                textColor: fg,
                mainBkg: bg,
                nodeBorder: accent,
                clusterBkg: "transparent",
                edgeLabelBackground: bg
            }
        });
        renderSequence += 1;
        var result = await mermaid.render("factsMermaid" + renderSequence, source);
        stage.innerHTML = result.svg;
        var svg = stage.querySelector("svg");
        if (svg) {
            svg.removeAttribute("height");
            svg.style.maxWidth = "100%";
            svg.style.height = "auto";
        }
    }

    async function renderSvg(source) {
        var parser = new DOMParser();
        var doc = parser.parseFromString(source, "image/svg+xml");
        var parserError = doc.querySelector("parsererror");
        if (parserError) {
            throw new Error("Invalid SVG markup");
        }
        var svg = doc.documentElement;
        if (!svg || svg.tagName.toLowerCase() !== "svg") {
            throw new Error("SVG root element missing");
        }
        var imported = document.importNode(svg, true);
        imported.removeAttribute("height");
        imported.style.maxWidth = "100%";
        imported.style.height = "auto";
        stage.innerHTML = "";
        stage.appendChild(imported);
    }

    async function renderHtml(source) {
        stage.innerHTML = source;
    }

    window.renderStudyMarkup = async function (kind, source, fgColorHex, bgColorHex, accentColorHex) {
        var fg = fgColorHex || "#E2E8F0";
        var bg = bgColorHex || "transparent";
        var accent = accentColorHex || fg;
        stage.style.color = fg;
        stage.style.background = bg;
        try {
            if (kind === "LATEX") {
                await renderLatex(source);
            } else if (kind === "MERMAID") {
                await renderMermaid(source, fg, bg, accent);
            } else if (kind === "SVG") {
                await renderSvg(source);
            } else if (kind === "HTML_CANVAS") {
                await renderHtml(source);
            } else {
                throw new Error("Unsupported markup kind: " + kind);
            }
            await waitForFonts();
            await afterPaint();
            var size = bounds();
            var payload = { ok: true, width: size.width, height: size.height };
            post(payload);
            return payload;
        } catch (error) {
            var message = error && error.message ? String(error.message) : String(error);
            var failure = { ok: false, width: 0, height: 0, error: message };
            post(failure);
            return failure;
        }
    };
})();
