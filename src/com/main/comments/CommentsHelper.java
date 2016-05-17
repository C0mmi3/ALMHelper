package com.main.comments;

import java.text.SimpleDateFormat;
import java.util.Calendar;

/**
 * Created by ikfl27 on 20.04.2016.
 */
/*
Содержит методы по формированию комментария.
 */
public class CommentsHelper {
    /*
    Создает новый комментарий
     */
    public static String createComment(String text, String credentials, String username) {
        Calendar cal = Calendar.getInstance();
        SimpleDateFormat sdf = new SimpleDateFormat("dd.MM.YYYY");
        String template =
                "<html><body>" +
                        "<br /> <br />" +
                        "<div align=\"left\"><font face=\"Arial\" color=\"#000080\">" +
                        "<span style=\"font-size:8pt\"><b>________________________________________</b></span></font>" +
                        "<font face=\"Arial\">" +
                        "<span style=\"font-size:8pt\">&nbsp;&nbsp;</span></font>" +
                        "</div>" +
                        "<div align=\"left\">" +
                        "<font face=\"Arial\" color=\"#000080\"><span style=\"font-size:8pt\"><b> &lt;&gt;" + ", " + sdf.format(cal.getTime()) + ": </b></span></font>" +
                        "<font face=\"Arial\"><span style=\"font-size:8pt\">" + text.replace("%DATE%", sdf.format(cal.getTime())) +
                        "</span></font></div><div align=\"left\">&nbsp;&nbsp;</div></body></html>";
        template = template.replace("&lt;", credentials + " " + "&lt;" + username);
        return template;
    }

    /*
    Добавляет комментарий к уже существующим
     */
    public static String addComment(String source, String text, String credentials, String username) {
        Calendar cal = Calendar.getInstance();
        SimpleDateFormat sdf = new SimpleDateFormat("dd.MM.YYYY");
        source = source.replace("\\n", "").replace("\\\"", "\"").replace("</body></html>", "");
        String template = source.substring(1, source.length() - 1) +
                ("<br /> <br />" +
                        "<div align=\"left\"><font face=\"Arial\" color=\"#000080\">" +
                        "<span style=\"font-size:8pt\"><b>________________________________________</b></span></font>" +
                        "<font face=\"Arial\">" +
                        "<span style=\"font-size:8pt\">&nbsp;&nbsp;</span></font>" +
                        "</div>" +
                        "<div align=\"left\">" +
                        "<font face=\"Arial\" color=\"#000080\"><span style=\"font-size:8pt\"><b> &lt;&gt;" + ", " + sdf.format(cal.getTime()) + ": </b></span></font>" +
                        "<font face=\"Arial\"><span style=\"font-size:8pt\">" + text.replace("%DATE%", sdf.format(cal.getTime())) +
                        "</span></font></div><div align=\"left\">&nbsp;&nbsp;</div></body></html>").replace("&lt;", credentials + " " + "&lt;" + username);
        return template;
    }
}
