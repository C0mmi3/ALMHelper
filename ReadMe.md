##### ALM Helper #####
----------------------------------------------------------------------------

Данный модуль предназначен для автоматизации работы с дефектами в системе ALM.
Модуль использует HP ALM REST API для обмена данными с системой баг-трекинга,
А также библиотеку GSON для работы с JSON-объектами.


#### Краткое описание функционала ####
----------------------------------------------------------------------------

1. Авторизация в ALM (POST HTTP/ALM REST API)
2. Получение дефекта из ALM (GET HTTP/ALM REST API)
3. Обновление дефекта в ALM (PUT HTTP/ALM REST API)
4. Получение списка дефектов

#### Функционал ####
----------------------------------------------------------------------------
1. Authenticate method.
Uses next arguments of config.properties:
 - alm.server.api.user = Username in ALM
 - alm.server.api.password = Password in ALM
As a result we're receiving cookies and user's session info.

2. getDefect method.
Input parameter - bug ID.
As a result we're receving JSON with a full information about bug

3. prepareDefect method.
Input parameter - JSON returned by getDefect method.
Output parameter - JSON with only neccessary attributes which need to be changed
 - OWNER
 - STATUS
 - DEV-COMMENTS

4. updateDefect method.
Updating the information about a bug in ALM.
Input parameter - JSON returned by prepareDefect method.

5. getList method.
config.properties contains attribute which points to where exists the file with a list of bugs which we should proceed:
 - alm.defects.list.file.path

6. archiveList method.
config.properties contains attribute which points to where should we store the history:
 - alm.helper.history.directory

7. signOut method.
Added 1.1.1 12.04.2016

8. Observer mode.
Added 1.2 14.04.2016

#### LAUNCH ####
----------------------------------------------------------------------------
В командной строке выполнить следующую команду:
Application works only in command line. Just use next command: java -jar /path-to-jar/ALMHelper.jar MODE
MODE: RUN_ONCE,WATCH
RUN_ONCE - Processing the list of bugs and then shutting down.
WATCH - Watching directory and processing if list appears.
If you won't assign MODE then application will start in RUN_ONCE mode.

#### История версий ####
----------------------------------------------------------------------------
1.0:
 - Basic functionality

1.1.1:
 - Sign out method
 - Closed status processing

1.2:
 - Watch mode
 - Log4j

1.3:
 - Fixed status processing
 - New logger for bugtickets
 - Cookies cleaning
 - CommentsHelper

2.0:
 - New architecture
 - Creation of new bugtickets.
 - Back up service.
 - Error processor
