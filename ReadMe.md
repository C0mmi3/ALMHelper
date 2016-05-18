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
*alm.server.api.user = Username in ALM
*alm.server.api.password = Password in ALM
As a result we're receiving cookies and user's session info.

2. getDefect method.
Input parameter - bug ID.
As a result we're receving JSON with a full information about bug

3. prepareDefect method.
Input parameter - JSON returned by getDefect method.
Output parameter - JSON with only neccessary attributes which need to be changed
* OWNER
* STATUS
* DEV-COMMENTS

4. updateDefect method.
Updating the information about a bug in ALM.
Input parameter - JSON returned by prepareDefect method.

5. getList method.
config.properties contains attribute which points to where exists the file with a list of bugs which we should proceed:
*alm.defects.list.file.path

6. archiveList method.
config.properties contains attribute which points to where should we store the history:
*alm.helper.history.directory

7. signOut method.
Added 1.1.1, 12.04.2016

8. Observer mode.
Added 1.2, 14.04.2016

#### LAUNCH ####
----------------------------------------------------------------------------
В командной строке выполнить следующую команду: java -jar /path-to-jar/ALMHelper.jar MODE
Где MODE: RUN_ONCE,WATCH
RUN_ONCE - единовременный запуск. Обработали список, выключились. Если не задать параметров запуска, то будет работать в этом режиме
WATCH - запуск в режиме наблюдателя. Модуль будет следить за появлением/изменением файла list.txt. Если он появился/изменился, то немедленно обработает его

#### ЛОГИРОВАНИЕ ####
----------------------------------------------------------------------------
Настройки логирования задаются в конфиге log4j2.xml. Сейчас имеется 3 логгера: ALMHelper, Processor, Observer.
ALMHelper и Processor пишут логи в файл helper.log и консоль
Observer пишет логи только в файл watchdog.log

#### История версий ####
----------------------------------------------------------------------------
1.0:
* Авторизация
* Получение дефекта
* Подготовка к апдейту дефекта
* Апдейт дефекта

1.1.1:
* Прерывание пользовательской сессии (sign-out)
* Обработка статуса Closed

1.2:
* Режим наблюдателя
* Логирование посредством log4j2

1.3:
* Унификация логики работы в режиме наблюдателя и в режиме единоразового запуска
* Обработка статуса Fixed
* Новый логгер для сохранения ошибок по дефектам
* Очистка cookies
* Новый служебный класс CommentsHelper для формирования комментариев к дефекту

2.0 (package com.test):
* Работа с ПРОМ ALM
Поддержка возможна благодаря общему базовому классу HelperBase. Многие методы общие как для ПРОМ ALM, так и для тестового.
Работа пока только началась.
* Резервное копирование исходной информации о дефекте. Откат изменений
* Переработанная и улучшенная архитектура модуля
Закончена работа по переносу функционала класса ALMHelper. TestHelper полностью работоспособен.
Базовая логика (аутентификация, выход, получение дефекта, апдейт дефекта, создание дефекта) вынесены в абстрактный класс HelperBase.
Там же определен абстрактный метод process(), который отвечает за общую логику работы. В TestHelper он полностью идентичен ALMHelper по функциональности.
* Рубильник для включения/выключения архивации списка дефектов
* Обработчик проблемных дефектов (Обработка ошибки 403)
Версия 2.0 пока сохраняет старую реализацию.