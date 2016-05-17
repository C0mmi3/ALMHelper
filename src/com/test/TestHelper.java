package com.test;

import com.google.gson.JsonObject;
import com.main.comments.CommentsHelper;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Iterator;
import java.util.List;

/**
 * Created by ikfl27 on 26.04.2016.
 * Класс для работы с ALM тестовых стендов (ИФТ/ПСИ)
 */
public class TestHelper extends HelperBase {
    private static final Logger logger = LogManager.getLogger("Defects");
    private String[] excludedUsers;
    private boolean archive = true;

    public TestHelper() {
        super();
        excludedUsers = getListOfExcludedUsers();
    }

    public void process() {
        JsonObject defect;
        List<Integer> array = new ArrayList<Integer>();

        log.info("--------------------------------------------------------");
        /*
        Получаем список дефектов из файла
         */
        try {
            array = getList();
        } catch (FileNotFoundException e) {
            log.error(e.toString());
        } catch (IOException e) {
            log.error(e.toString());
        }
        /*
        Отправляем запрос на аутентификацию в ALM
         */
        Authenticate();
        JsonObject ob;
        String project = config.getProperty("alm.server.api.project");
        /*
        Последовательно получаем дефекты, обрабатываем их и апдейтим в ALM
         */
        Iterator<Integer> iterator = array.iterator();
        while (iterator.hasNext()) {
            try {
                defect = getDefect(project, iterator.next());
                ob = prepareDefect(defect);
                updateDefect(project, ob);
            } catch (RuntimeException e) {
                log.error(e.toString());
                log.error("Fetching next defect. Previous defect hasn't been updated");
                continue;
            } catch (IOException e) {
                log.error(e.toString());
                continue;
            }
        }
        signOut();
        try {
            /*
            Архивируем список дефектов текущей поставки
             */
            archiveList();
        } catch (IOException e) {
            log.error(e.toString());
        }
    }

    public List<Integer> getList() throws IOException {
        log.info("Receiving list of fixed defects");
        List<Integer> array = new ArrayList<Integer>();
        Path path = Paths.get(config.getProperty("alm.defects.list.file.path") + "list.txt");
        String i;
        if (Files.exists(path)) {
            File file = new File(path.toString());
            BufferedReader reader = new BufferedReader(new FileReader(file));
            while ((i = reader.readLine()) != null) {
                array.add(Integer.parseInt(i));
            }
            reader.close();
            /*
            Проверка на дубликаты дефектов в списке
             */
            for (int j = 0; j < array.size(); j++) {
                for (int k = 0; k < array.size(); k++) {
                    if (j != k) {
                        if (array.get(j).equals(array.get(k))) array.remove(j);
                    }
                }
            }
            log.info("List of defects has been received!");
        } else throw new FileNotFoundException("The list file doesn't exist or have an inappropriate name!");
        return array;
    }


    /*
Метод, позволяющий получить JSON-объект с готовыми к отправке в ALM изменениями.
*/
    public JsonObject prepareDefect(JsonObject obj) throws IOException, RuntimeException {
        log.info("Preparing Defect #" + obj.get("id").toString() + " for update");
        JsonObject object = new JsonObject();
        Calendar cal = Calendar.getInstance();
        String devComment;
        SimpleDateFormat sdf = new SimpleDateFormat("dd.MM.YYYY");
        if (obj.get("dev-comments").toString().equals("null")) {
            devComment = CommentsHelper.createComment(config.getProperty("alm.defect.comment"), config.getProperty("alm.server.api.credentials"), config.getProperty("alm.server.api.user"));
        } else {
            String source = obj.get("dev-comments").toString();
            devComment = CommentsHelper.addComment(source, config.getProperty("alm.defect.comment"), config.getProperty("alm.server.api.credentials"), config.getProperty("alm.server.api.user"));
        }
        //Если дефект в статусе Closed, то не трогаем его
        if (obj.get("status").toString().replace("\"", "").equals("Closed")) {
            logger.error("Defect #" + obj.get("id").toString() + " is already Closed");
            throw new RuntimeException("Defect #" + obj.get("id").toString() + " is already Closed");
        } else if (obj.get("status").toString().replace("\"", "").equals("Fixed")) {
            logger.error("Defect #" + obj.get("id").toString() + " is already Fixed");
            throw new RuntimeException("Defect #" + obj.get("id").toString() + " is already Fixed");
        } else {
            {
            /*
            В этом блоке мы проверяем, что автора дефекта нет в списке пользователей, перевод дефектов на которых запрещен.
            Если есть, то возвращаем на пользователя по умолчанию. Если нет, то возвращаем на автора
             */
                String owner;
                for (int i = 0; i < excludedUsers.length; i++) {
                    if (obj.get("detected-by").toString().replace("\"", "").equals(excludedUsers[i])) {
                        object.addProperty("owner", config.getProperty("alm.default.user"));
                        break;
                    } else {
                        owner = obj.get("detected-by").toString();
                        owner = owner.substring(1, owner.length() - 1);
                        object.addProperty("owner", owner);
                    }
                }
                log.debug(devComment + "\n--------------------------------------------------\n");
                object.addProperty("dev-comments", devComment);
                object.addProperty("status", "Fixed");
                object.addProperty("id", obj.get("id").toString());
                log.info("Defect has been prepared!");
                return object;
            }
        }
    }

    public String[] getListOfExcludedUsers() {
        log.info("Receiving list of excluded users");
        String start = config.getProperty("alm.exclude.users");
        String[] excludeUsers = start.split(",");
        log.info("List has been received!");
        return excludeUsers;
    }

    /*
Метод для архивирования списка дефектов текущей поставки.
Метод копирует файл в заданную директорию и меняет имя файла на текущую дату.
Т.о. мы знаем составы всех поставок по датам.
 */
    public void archiveList() throws IOException {
        setArchive(config.getProperty("alm.helper.history.save").equals("true") ? true : false);
        if (getArchive()) {
            Calendar cal = Calendar.getInstance();
            SimpleDateFormat sdf = new SimpleDateFormat("dd.MM.YYYY");
            Path sourcePath = Paths.get(config.getProperty("alm.defects.list.file.path") + "list.txt");
            Path destinationPath = Paths.get(config.getProperty("alm.helper.history.directory") + sdf.format(cal.getTime()) + ".txt");
            Files.copy(sourcePath, destinationPath, StandardCopyOption.REPLACE_EXISTING);
            Files.delete(sourcePath);
            log.info("The list of defects has been saved and will be moved to the Archive.");
        } else log.info("Archivation has been turned off.");
    }


    public void setArchive(boolean archive) {
        this.archive = archive;
    }

    public boolean getArchive() {
        return this.archive;
    }

}
