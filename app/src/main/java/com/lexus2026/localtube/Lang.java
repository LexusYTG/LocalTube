package com.lexus2026.localtube;

import java.util.*;

public class Lang {

    private static String currentLang = "es";

    public static void setLang(String lang) {
        currentLang = lang;
    }

    public static String getLang() {
        return currentLang;
    }

    private static final Map<String, String[]> T = new HashMap<>();
    // Index: 0=es, 1=en, 2=ru, 3=de, 4=zh

    static {
        // ── SettingsActivity ──────────────────────────────────────────────
        T.put("settings_title",          new String[]{"Configuracion",           "Settings",                    "Настройки",                  "Einstellungen",              "设置"});
        T.put("section_library",         new String[]{"Biblioteca",              "Library",                     "Библиотека",                 "Bibliothek",                 "媒体库"});
        T.put("section_playback",        new String[]{"Reproduccion",            "Playback",                    "Воспроизведение",            "Wiedergabe",                 "播放"});
        T.put("section_language",        new String[]{"Idioma",                  "Language",                    "Язык",                       "Sprache",                    "语言"});
        T.put("folder_root",             new String[]{"Carpeta raiz",            "Root folder",                 "Корневая папка",             "Stammordner",                "根目录"});
        T.put("folder_none",             new String[]{"No configurada",          "Not set",                     "Не задана",                  "Nicht festgelegt",           "未设置"});
        T.put("folder_pick",             new String[]{"Elegir carpeta",          "Choose folder",               "Выбрать папку",              "Ordner wählen",              "选择文件夹"});
        T.put("folder_set",              new String[]{"Carpeta configurada",     "Folder set",                  "Папка задана",               "Ordner festgelegt",          "文件夹已设置"});
        T.put("scan_title",              new String[]{"Reescanear biblioteca",   "Rescan library",              "Пересканировать библиотеку", "Bibliothek neu scannen",     "重新扫描媒体库"});
        T.put("scan_desc",               new String[]{"Busca videos nuevos y actualiza la base", "Find new videos and update the index", "Поиск новых видео и обновление базы", "Neue Videos suchen und Index aktualisieren", "查找新视频并更新索引"});
        T.put("scan_btn",                new String[]{"Escanear ahora",          "Scan now",                    "Сканировать",                "Jetzt scannen",              "立即扫描"});
        T.put("scan_running",            new String[]{"Escaneando...",           "Scanning...",                 "Сканирование...",            "Wird gescannt...",           "扫描中..."});
        T.put("scan_done",               new String[]{" videos indexados",       " videos indexed",             " видео проиндексировано",    " Videos indiziert",          " 个视频已索引"});
        T.put("scan_error",              new String[]{"Error: ",                 "Error: ",                     "Ошибка: ",                   "Fehler: ",                   "错误: "});
        T.put("scan_no_root",            new String[]{"Primero elige la carpeta raiz", "Choose the root folder first", "Сначала выберите корневую папку", "Wählen Sie zuerst den Stammordner", "请先选择根目录"});

        // ── Reindexado completo (long-press en Escanear) ──────────────────
        T.put("scan_reindex_desc",       new String[]{"Manten presionado \"Escanear ahora\" para recrear el indice completo", "Long-press \"Scan now\" to rebuild the full index", "Долгое нажатие на \"Сканировать\" для пересоздания индекса", "\"Jetzt scannen\" lange drücken, um den Index neu aufzubauen", "长按\"立即扫描\"以重建完整索引"});
        T.put("scan_reindex_title",      new String[]{"Recrear indice completo", "Rebuild full index",          "Пересоздать полный индекс",  "Vollstandigen Index neu aufbauen", "重建完整索引"});
        T.put("scan_reindex_confirm",    new String[]{"Se borraran todos los archivos de indice y las miniaturas generadas (excepto las caratulas de canal). Luego se reescaneara todo desde cero.\n\n¿Continuar?", "All index files and generated thumbnails will be deleted (except channel covers). Then everything will be rescanned from scratch.\n\nContinue?", "Все файлы индексов и созданные миниатюры будут удалены (кроме обложек каналов). Затем всё будет пересканировано с нуля.\n\nПродолжить?", "Alle Indexdateien und generierten Miniaturen (außer Kanalbildern) werden gelöscht. Anschließend wird alles neu gescannt.\n\nFortfahren?", "将删除所有索引文件和已生成的缩略图（频道封面除外），然后从头重新扫描全部内容。\n\n是否继续？"});
        T.put("scan_reindex_btn",        new String[]{"Recrear",                 "Rebuild",                     "Пересоздать",                "Neu aufbauen",               "重建"});
        T.put("scan_reindex_running",    new String[]{"Borrando indice...",      "Wiping index...",             "Удаление индекса...",        "Index wird gelöscht...",     "正在清除索引..."});
        T.put("scan_reindex_done",       new String[]{"Indice recreado: ",       "Index rebuilt: ",             "Индекс пересоздан: ",        "Index neu aufgebaut: ",      "索引已重建："});

        T.put("channels_title",          new String[]{"Canales",                 "Channels",                    "Каналы",                     "Kanäle",                     "频道"});
        T.put("channels_desc",           new String[]{"Renombra un canal o cambia su foto", "Rename a channel or change its photo", "Переименовать канал или сменить фото", "Kanal umbenennen oder Foto ändern", "重命名频道或更改图片"});
        T.put("channels_btn",            new String[]{"Gestionar canales",       "Manage channels",             "Управление каналами",        "Kanäle verwalten",           "管理频道"});
        T.put("channels_empty",          new String[]{"No hay canales todavia. Escanea la biblioteca primero.", "No channels yet. Scan the library first.", "Каналов нет. Сначала отсканируйте библиотеку.", "Noch keine Kanäle. Zuerst Bibliothek scannen.", "暂无频道，请先扫描媒体库。"});
        T.put("channel_rename",          new String[]{"Cambiar nombre",          "Rename",                      "Переименовать",              "Umbenennen",                 "重命名"});
        T.put("channel_photo",           new String[]{"Cambiar foto",            "Change photo",                "Сменить фото",               "Foto ändern",                "更换图片"});
        T.put("channel_rename_title",    new String[]{"Cambiar nombre",          "Rename",                      "Переименовать",              "Umbenennen",                 "重命名"});
        T.put("channel_save",            new String[]{"Guardar",                 "Save",                        "Сохранить",                  "Speichern",                  "保存"});
        T.put("channel_saved",           new String[]{"Nombre actualizado",      "Name updated",                "Имя обновлено",              "Name aktualisiert",          "名称已更新"});
        T.put("channel_photo_saved",     new String[]{"Foto de canal actualizada", "Channel photo updated",     "Фото канала обновлено",      "Kanalbild aktualisiert",     "频道图片已更新"});
        T.put("channel_photo_error",     new String[]{"No se pudo guardar la foto", "Could not save the photo", "Не удалось сохранить фото",  "Foto konnte nicht gespeichert werden", "无法保存图片"});
        T.put("cancel",                  new String[]{"Cancelar",                "Cancel",                      "Отмена",                     "Abbrechen",                  "取消"});
        T.put("close",                   new String[]{"Cerrar",                  "Close",                       "Закрыть",                    "Schließen",                  "关闭"});
        T.put("bg_audio_title",          new String[]{"Audio en segundo plano",  "Background audio",            "Фоновое аудио",              "Hintergrundaudio",           "后台音频"});
        T.put("bg_audio_desc",           new String[]{"Sigue reproduciendo al minimizar", "Keep playing when minimized", "Продолжает воспроизведение при сворачивании", "Weiterspielen beim Minimieren", "最小化后继续播放"});
        T.put("autoplay_title",          new String[]{"Autoplay",                "Autoplay",                    "Автовоспроизведение",        "Autoplay",                   "自动播放"});
        T.put("autoplay_desc",           new String[]{"Reproducir automaticamente al abrir", "Play automatically on open", "Автоматически воспроизводить при открытии", "Beim Öffnen automatisch abspielen", "打开时自动播放"});
        T.put("lang_interface",          new String[]{"Idioma de la interfaz",   "Interface language",          "Язык интерфейса",            "Oberflächensprache",         "界面语言"});
        T.put("lang_select_title",       new String[]{"Seleccionar idioma",      "Select language",             "Выбрать язык",               "Sprache auswählen",          "选择语言"});
        T.put("lang_changed",            new String[]{"Idioma: ",                "Language: ",                  "Язык: ",                     "Sprache: ",                  "语言："});
        T.put("lang_btn",                new String[]{"Cambiar idioma",          "Change language",             "Сменить язык",               "Sprache ändern",             "更改语言"});
        T.put("rename_error",            new String[]{"No se pudo renombrar (¿ya existe ese nombre?)", "Could not rename (does that name already exist?)", "Не удалось переименовать (такое имя уже есть?)", "Umbenennen fehlgeschlagen (Name bereits vorhanden?)", "重命名失败（名称是否已存在？）"});

        // ── MainActivity / navegación ─────────────────────────────────────
        T.put("app_name",                new String[]{"LocalTube",               "LocalTube",                   "LocalTube",                  "LocalTube",                  "LocalTube"});
        T.put("rename",                  new String[]{"Renombrar",               "Rename",                      "Переименовать",              "Umbenennen",                 "重命名"});
        T.put("tab_home",               new String[]{"Inicio",                   "Home",                        "Главная",                    "Startseite",                 "主页"});
        T.put("tab_shorts",             new String[]{"Shorts",                   "Shorts",                      "Shorts",                     "Shorts",                     "短视频"});
        T.put("tab_history",            new String[]{"Historial",                "History",                     "История",                    "Verlauf",                    "历史"});
        T.put("tab_movies",             new String[]{"Peliculas",                "Movies",                      "Фильмы",                     "Filme",                      "电影"});
        T.put("scan_loading",           new String[]{"Escaneando biblioteca...", "Scanning library...",         "Сканирование библиотеки...", "Bibliothek wird gescannt...","正在扫描媒体库..."});
        T.put("scan_progress",          new String[]{"Escaneando ",              "Scanning ",                   "Сканирование ",              "Scannen ",                   "扫描中 "});
        T.put("scan_no_videos",         new String[]{"No se encontraron videos en\n", "No videos found in\n",  "Видео не найдены в\n",      "Keine Videos gefunden in\n","未找到视频\n"});
        T.put("permission_denied",      new String[]{"Permiso denegado",         "Permission denied",           "Разрешение отклонено",       "Berechtigung verweigert",    "权限被拒绝"});
        T.put("permission_files",       new String[]{"Permiso denegado\n\nConcede \"Acceso a todos los archivos\" para continuar", "Permission denied\n\nGrant \"All files access\" to continue", "Разрешение отклонено\n\nПредоставьте доступ \"Ко всем файлам\" для продолжения", "Berechtigung verweigert\n\nGewähren Sie \"Zugriff auf alle Dateien\" zum Fortfahren", "权限被拒绝\n\n请授予\"所有文件访问权限\"以继续"});
        T.put("videos_indexed",         new String[]{" videos indexados",         " videos indexed",             " видео проиндексировано",    " Videos indiziert",          " 个视频已索引"});
        T.put("error_prefix",           new String[]{"Error: ",                  "Error: ",                     "Ошибка: ",                   "Fehler: ",                   "错误: "});

        // ── PlayerActivity / SeriesPlayerActivity ─────────────────────────
        T.put("no_next",                 new String[]{"No hay siguiente video",  "No next video",               "Следующего видео нет",       "Kein nächstes Video",        "没有下一个视频"});
        T.put("no_prev",                 new String[]{"No hay video anterior",   "No previous video",           "Предыдущего видео нет",      "Kein vorheriges Video",      "没有上一个视频"});
        T.put("open_error",              new String[]{"No se pudo abrir el video", "Could not open the video",  "Не удалось открыть видео",   "Video konnte nicht geöffnet werden", "无法打开视频"});
        T.put("chapter_change",          new String[]{"Cambiar capítulo",        "Change chapter",              "Сменить главу",              "Kapitel wechseln",           "切换章节"});
        T.put("chapter_error",           new String[]{"No se pudo cambiar el capítulo", "Could not change chapter", "Не удалось сменить главу", "Kapitel konnte nicht gewechselt werden", "无法切换章节"});
        T.put("chapter_invalid",         new String[]{"Número inválido",         "Invalid number",              "Неверный номер",             "Ungültige Nummer",           "无效编号"});
        T.put("chapter_abbr",            new String[]{"Cap. ?",                  "Ch. ?",                       "Гл. ?",                      "Kap. ?",                     "章 ?"});
        T.put("channel_label",           new String[]{"Canal",                   "Channel",                     "Канал",                      "Kanal",                      "频道"});
        T.put("tags_label",              new String[]{"Tags",                    "Tags",                        "Теги",                       "Tags",                       "标签"});
        T.put("end_label",               new String[]{"Fin",                     "End",                         "Конец",                      "Ende",                       "结束"});
        T.put("end_of",                  new String[]{"Fin de ",                 "End of ",                     "Конец ",                     "Ende von ",                  "结束："});
        T.put("chapters_label",          new String[]{" capítulos",              " chapters",                   " главы",                     " Kapitel",                   " 章"});
        T.put("items_label",             new String[]{" elementos",              " items",                      " элементов",                 " Elemente",                  " 项"});

        // ── Metadata de video ─────────────────────────────────────────────
        T.put("unit_b",                  new String[]{"B",                       "B",                           "Б",                          "B",                          "B"});
        T.put("unit_kb",                 new String[]{"KB",                      "KB",                          "КБ",                         "KB",                         "KB"});
        T.put("unit_mb",                 new String[]{"MB",                      "MB",                          "МБ",                         "MB",                         "MB"});
        T.put("unit_gb",                 new String[]{"GB",                      "GB",                          "ГБ",                         "GB",                         "GB"});
        T.put("res_4k",                  new String[]{"4K",                      "4K",                          "4K",                         "4K",                         "4K"});
        T.put("chapter_prefix",          new String[]{"Capítulo ",               "Chapter ",                    "Глава ",                      "Kapitel ",                   "第 "});
        T.put("chapter_none",            new String[]{"Capítulo",                "Chapter",                     "Глава",                       "Kapitel",                    "章节"});
        T.put("cap_abbr",                new String[]{"cap",                     "ep",                          "эп",                         "Ep.",                         "集"});
        T.put("resume_prefix",           new String[]{"▶ Retomar en ",           "▶ Resume at ",                "▶ Продолжить с ",             "▶ Fortfahren bei ",          "▶ 继续播放 "});
        T.put("time_now",                new String[]{"Ahora",                   "Just now",                    "Только что",                  "Gerade eben",                "刚刚"});
        T.put("time_mins_ago",           new String[]{"Hace ",                   "  min ago",                   " мин назад",                  " vor ",                      " 分钟前"});
        T.put("time_mins_ago_suffix",    new String[]{" min",                    "",                            "",                            " min",                       ""});
        T.put("time_hours_ago",          new String[]{"Hace ",                   " h ago",                      " ч назад",                    " vor ",                      " 小时前"});
        T.put("time_hours_ago_suffix",   new String[]{" h",                      "",                            "",                            " Std.",                      ""});
        T.put("time_days_ago",           new String[]{"Hace ",                   " d ago",                      " д назад",                    " vor ",                      " 天前"});
        T.put("time_days_ago_suffix",    new String[]{" día",                    "",                            "",                            " Tag",                       ""});
        T.put("time_days_ago_plural",    new String[]{"s",                       "",                            "",                            "en",                         ""});
        T.put("date_locale",             new String[]{"es",                      "en",                          "ru",                          "de",                         "zh"});

        // ── SearchActivity ────────────────────────────────────────────────
        T.put("search_hint",             new String[]{"Buscar videos...",        "Search videos...",            "Поиск видео...",             "Videos suchen...",           "搜索视频..."});
        T.put("search_empty",            new String[]{"Escribe algo para buscar", "Type something to search",  "Введите запрос для поиска",  "Etwas eingeben zum Suchen",  "输入内容以搜索"});
        T.put("search_no_results",       new String[]{"Sin resultados",          "No results",                  "Нет результатов",            "Keine Ergebnisse",           "无结果"});

        // ── HistoryFragment ───────────────────────────────────────────────
        T.put("history_empty",           new String[]{"Sin historial\n\nLos videos que reproduzcas aparecerán acá", "No history\n\nVideos you play will appear here", "История пуста\n\nВоспроизведённые видео появятся здесь", "Kein Verlauf\n\nAbgespielte Videos erscheinen hier", "暂无记录\n\n播放过的视频将显示在这里"});

        // ── SeriesFragment ────────────────────────────────────────────────
        T.put("series_empty",            new String[]{"Sin series\n\nPone carpetas de episodios dentro de Series/", "No series\n\nPut episode folders inside Series/", "Нет сериалов\n\nПоместите папки с эпизодами в Series/", "Keine Serien\n\nEpisoden-Ordner in Series/ ablegen", "暂无剧集\n\n请将剧集文件夹放入 Series/"});

        // ── ShortsFragment ────────────────────────────────────────────────
        T.put("shorts_label",            new String[]{"Shorts",                  "Shorts",                      "Shorts",                     "Shorts",                     "短视频"});
        T.put("shorts_empty",            new String[]{"Sin shorts.\nPone videos verticales en la carpeta Shorts/", "No shorts.\nPut vertical videos in the Shorts/ folder", "Нет коротких видео.\nПоместите вертикальные видео в папку Shorts/", "Keine Shorts.\nVertikale Videos in den Ordner Shorts/ legen", "暂无短视频。\n请将竖屏视频放入 Shorts/ 文件夹"});
    }

    public static String get(String key) {
        String[] vals = T.get(key);
        if (vals == null) return key;
        int idx = langIndex(currentLang);
        return vals[idx];
    }

    private static int langIndex(String lang) {
        switch (lang) {
            case "en": return 1;
            case "ru": return 2;
            case "de": return 3;
            case "zh": return 4;
            default:   return 0; // "es"
        }
    }
}
