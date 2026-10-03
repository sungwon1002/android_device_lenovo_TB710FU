#!/usr/bin/env python3
#
# SPDX-FileCopyrightText: 2026 The LineageOS Project
# SPDX-License-Identifier: Apache-2.0
#
# Generates parts/res/values*/strings.xml for TB710FUParts (Lenovo features).
# The game performance and Play Store identity strings live with their app
# (vendor/lenovo/TB710FU-custom/tools/custom_strings.py).
# English is the default; strings equal to English are left out of the
# translations so they fall back to it.
import os, re

RES = os.path.join(os.path.dirname(os.path.abspath(__file__)), '..', 'parts', 'res')

KEYS = [
 ('app_name', None), ('app_summary', None),
 ('charging_category', 'Charging'),
 ('charging_normal_title', None), ('charging_normal_summary', None),
 ('charging_limit_title', None), ('charging_limit_summary', None),
 ('charging_protect_title', None), ('charging_protect_summary', None),
 ('battery_category', 'Battery'),
 ('bypass_title', None), ('bypass_summary', None),
 ('standby_title', None), ('standby_summary', None),
 ('battery_maintenance_title', None), ('battery_maintenance_summary', None),
 ('battery_info_title', None), ('battery_info_summary', None),
 ('display_category', 'Display'),
 ('white_balance_title', None), ('white_balance_summary', None),
 ('white_balance_strength_title', None), ('white_balance_strength_summary', None),
 ('pen_category', 'Pen'), ('pen_settings_title', None), ('pen_settings_summary', None),
 ('keyboard_category', 'Keyboard'),
 ('folio_category', None),
 ('memory_category', 'Memory'), ('vram_title', None), ('vram_off', None), ('vram_size', None),
 ('vram_summary', None), ('vram_summary_pending', None),
 ('memory_status_title', None), ('memory_status_summary', None),
 ('memory_status_summary_off', None),
 ('reboot_title', None), ('reboot_message', None), ('reboot_now', None), ('reboot_later', None),
]
ARRAYS = ['app_key_entries']

L = {}

L['en'] = dict(
 white_balance_strength_title='Strength', white_balance_strength_summary='How strongly the screen follows the ambient light. 100 % matches the stock Lenovo setting.',
 app_name='Lenovo features',
 app_summary='Battery, pen, physical keyboard, virtual memory',
 charging_category='Charging',
 charging_normal_title='Normal', charging_normal_summary='Charges to 100%',
 charging_limit_title='Stop at 80%', charging_limit_summary='Charging stops at 80% and resumes below 76%',
 charging_protect_title='Battery protection',
 charging_protect_summary='Keeps the battery between 40% and 60% for a tablet that stays plugged in',
 battery_category='Battery',
 bypass_title='Bypass charging',
 bypass_summary='While plugged in, the tablet runs directly on charger power and the battery is not charged. Reduces heat while gaming. Charging resumes below 20%.',
 standby_title='Enhanced standby',
 standby_summary='Puts idle apps to sleep a few minutes after the screen turns off, so the battery lasts longer in standby',
 battery_maintenance_title='Battery health optimization',
 battery_maintenance_summary='Lowers the charging voltage step by step as the battery ages',
 battery_info_title='Battery status', battery_info_summary='Charge cycles %1$s · Health %2$s',
 display_category='Display',
 white_balance_title='Adaptive color tone',
 white_balance_summary='Adjusts the screen color temperature to the ambient light so it looks natural',
 pen_category='Pen', pen_settings_title='Lenovo pen',
 pen_settings_summary='Pen buttons, writing vibration, pairing',
 keyboard_category='Keyboard',
 folio_category='Folio case',
 memory_category='Memory', vram_title='Memory extension', vram_off='Off', vram_size='%1$d GB',
 vram_summary='%1$s. Uses storage as extra memory for apps kept in the background.',
 vram_summary_pending='%1$s after restart (now: %2$s)',
 memory_status_title='Current memory',
 memory_status_summary='RAM %1$s + %2$s (in use %3$s)',
 memory_status_summary_off='RAM %1$s (in use %2$s)',
 reboot_title='Restart required',
 reboot_message='The new memory extension size is used after the tablet restarts. Restart now?',
 reboot_now='Restart', reboot_later='Later',
 app_key_entries=['None', 'Screenshot', 'Notifications', 'Quick settings', 'Recent apps',
                  'Assistant', 'Search', 'Settings'],
)

L['ko'] = dict(
 white_balance_strength_title='강도', white_balance_strength_summary='주변 조명에 따라 화면 색을 바꾸는 정도입니다. 100%는 순정 Lenovo 설정과 같습니다.',
 app_name='Lenovo 기능',
 app_summary='배터리, 펜, 물리 키보드, 가상 메모리',
 charging_category='충전',
 charging_normal_title='일반', charging_normal_summary='100%까지 충전합니다',
 charging_limit_title='80%에서 충전 중지', charging_limit_summary='80%에서 충전을 멈추고 76% 아래로 내려가면 다시 충전합니다',
 charging_protect_title='배터리 보호',
 charging_protect_summary='항상 충전기에 연결해 두는 경우 배터리를 40~60%로 유지합니다',
 battery_category='배터리',
 bypass_title='우회 충전',
 bypass_summary='충전기 연결 중 배터리를 충전하지 않고 충전기 전원으로 바로 작동합니다. 게임 중 발열이 줄어듭니다. 20% 아래로 내려가면 다시 충전합니다.',
 standby_title='대기 절전 강화',
 standby_summary='화면이 꺼지고 몇 분 뒤 사용하지 않는 앱을 절전 상태로 전환해 대기 시간을 늘립니다',
 battery_maintenance_title='배터리 수명 최적화',
 battery_maintenance_summary='배터리 사용 기간에 따라 충전 전압을 단계적으로 낮춥니다',
 battery_info_title='배터리 상태', battery_info_summary='충전 횟수 %1$s · 성능 %2$s',
 display_category='디스플레이',
 white_balance_title='자연스러운 색감',
 white_balance_summary='주변 조명에 맞춰 화면 색온도를 자연스럽게 조절합니다',
 pen_category='펜', pen_settings_title='Lenovo 펜', pen_settings_summary='펜 버튼, 필기 진동, 연결',
 keyboard_category='키보드',
 folio_category='폴리오 케이스',
 memory_category='메모리', vram_title='메모리 확장', vram_off='사용 안함', vram_size='%1$d GB',
 vram_summary='%1$s. 백그라운드 앱을 위해 저장공간 일부를 메모리로 사용합니다.',
 vram_summary_pending='재시작 후 %1$s (현재: %2$s)',
 memory_status_title='현재 메모리',
 memory_status_summary='RAM %1$s + %2$s (사용 중 %3$s)',
 memory_status_summary_off='RAM %1$s (사용 중 %2$s)',
 reboot_title='재시작 필요',
 reboot_message='새 메모리 확장 크기는 태블릿을 다시 시작한 뒤 적용됩니다. 지금 다시 시작할까요?',
 reboot_now='다시 시작', reboot_later='나중에',
 app_key_entries=['없음', '스크린샷', '알림', '빠른 설정', '최근 앱', '어시스턴트', '검색', '설정'],
)

L['ja'] = dict(
 white_balance_strength_title='強さ', white_balance_strength_summary='周囲の光に合わせて画面の色を変える度合いです。100%は標準のLenovo設定と同じです。',
 app_name='Lenovo 機能', app_summary='バッテリー、ペン、物理キーボード、仮想メモリ',
 charging_category='充電',
 charging_normal_title='標準', charging_normal_summary='100%まで充電します',
 charging_limit_title='80%で充電を停止', charging_limit_summary='80%で充電を停止し、76%を下回ると再開します',
 charging_protect_title='バッテリー保護',
 charging_protect_summary='常に電源に接続して使う場合、バッテリーを40～60%に保ちます',
 battery_category='バッテリー',
 bypass_title='バイパス充電',
 bypass_summary='電源接続中はバッテリーを充電せず、充電器の電力で直接動作します。ゲーム中の発熱を抑えます。20%を下回ると充電を再開します。',
 standby_title='スタンバイ省電力の強化',
 standby_summary='画面オフの数分後に使っていないアプリをスリープさせ、待機時間を延ばします',
 battery_maintenance_title='バッテリー寿命の最適化',
 battery_maintenance_summary='バッテリーの劣化に合わせて充電電圧を段階的に下げます',
 battery_info_title='バッテリーの状態', battery_info_summary='充電回数 %1$s · 状態 %2$s',
 display_category='ディスプレイ',
 white_balance_title='自然な色合い',
 white_balance_summary='周囲の光に合わせて画面の色温度を自然に調整します',
 pen_category='ペン', pen_settings_title='Lenovo ペン', pen_settings_summary='ペンボタン、書き心地の振動、ペアリング',
 keyboard_category='キーボード',
 folio_category='Folio case',
 memory_category='メモリ', vram_title='メモリ拡張', vram_off='オフ', vram_size='%1$d GB',
 vram_summary='%1$s。ストレージの一部をバックグラウンドアプリ用のメモリとして使います。',
 vram_summary_pending='再起動後 %1$s（現在: %2$s）',
 memory_status_title='現在のメモリ',
 memory_status_summary='RAM %1$s + %2$s（使用中 %3$s）',
 memory_status_summary_off='RAM %1$s（使用中 %2$s）',
 reboot_title='再起動が必要です',
 reboot_message='新しいメモリ拡張サイズはタブレットの再起動後に適用されます。今すぐ再起動しますか？',
 reboot_now='再起動', reboot_later='後で',
 app_key_entries=['なし', 'スクリーンショット', '通知', 'クイック設定', '最近のアプリ',
                  'アシスタント', '検索', '設定'],
)

L['zh-rCN'] = dict(
 white_balance_strength_title='强度', white_balance_strength_summary='屏幕随环境光改变颜色的程度。100% 与原厂 Lenovo 设置相同。',
 app_name='Lenovo 功能', app_summary='电池、笔、物理键盘、虚拟内存',
 charging_category='充电',
 charging_normal_title='标准', charging_normal_summary='充电至 100%',
 charging_limit_title='充至 80% 停止', charging_limit_summary='充至 80% 时停止，低于 76% 时恢复充电',
 charging_protect_title='电池保护',
 charging_protect_summary='长期插电使用时，将电量保持在 40%～60%',
 battery_category='电池',
 bypass_title='旁路充电',
 bypass_summary='插电时不给电池充电，由充电器直接为平板供电，减少游戏发热。电量低于 20% 时恢复充电。',
 standby_title='增强待机省电',
 standby_summary='屏幕关闭几分钟后让闲置应用进入休眠，延长待机时间',
 battery_maintenance_title='电池寿命优化',
 battery_maintenance_summary='随电池老化逐步降低充电电压',
 battery_info_title='电池状态', battery_info_summary='充电次数 %1$s · 健康度 %2$s',
 display_category='显示',
 white_balance_title='自然色调',
 white_balance_summary='根据环境光自动调节屏幕色温，使显示更自然',
 pen_category='手写笔', pen_settings_title='Lenovo 手写笔', pen_settings_summary='笔按键、书写振动、配对',
 keyboard_category='键盘',
 folio_category='Folio case',
 memory_category='内存', vram_title='内存扩展', vram_off='关闭', vram_size='%1$d GB',
 vram_summary='%1$s。将部分存储空间用作后台应用的内存。',
 vram_summary_pending='重启后为 %1$s（当前：%2$s）',
 memory_status_title='当前内存',
 memory_status_summary='RAM %1$s + %2$s（已用 %3$s）',
 memory_status_summary_off='RAM %1$s（已用 %2$s）',
 reboot_title='需要重启', reboot_message='新的内存扩展大小将在平板重启后生效。立即重启？',
 reboot_now='重启', reboot_later='稍后',
 app_key_entries=['无', '截屏', '通知', '快捷设置', '最近应用', '助理', '搜索', '设置'],
)

L['zh-rTW'] = dict(
 white_balance_strength_title='強度', white_balance_strength_summary='螢幕隨環境光改變色彩的程度。100% 與原廠 Lenovo 設定相同。',
 app_name='Lenovo 功能', app_summary='電池、筆、實體鍵盤、虛擬記憶體',
 charging_category='充電',
 charging_normal_title='標準', charging_normal_summary='充電至 100%',
 charging_limit_title='充至 80% 停止', charging_limit_summary='充至 80% 時停止，低於 76% 時恢復充電',
 charging_protect_title='電池保護',
 charging_protect_summary='長時間插電使用時，將電量維持在 40%～60%',
 battery_category='電池',
 bypass_title='旁路充電',
 bypass_summary='插電時不為電池充電，由充電器直接供電給平板，減少遊戲發熱。電量低於 20% 時恢復充電。',
 standby_title='強化待機省電',
 standby_summary='螢幕關閉幾分鐘後讓閒置應用程式進入休眠，延長待機時間',
 battery_maintenance_title='電池壽命最佳化',
 battery_maintenance_summary='隨電池老化逐步降低充電電壓',
 battery_info_title='電池狀態', battery_info_summary='充電次數 %1$s · 健康度 %2$s',
 display_category='顯示',
 white_balance_title='自然色調',
 white_balance_summary='依環境光自動調整螢幕色溫，讓顯示更自然',
 pen_category='觸控筆', pen_settings_title='Lenovo 觸控筆', pen_settings_summary='筆按鍵、書寫震動、配對',
 keyboard_category='鍵盤',
 folio_category='Folio case',
 memory_category='記憶體', vram_title='記憶體擴充', vram_off='關閉', vram_size='%1$d GB',
 vram_summary='%1$s。將部分儲存空間作為背景應用程式的記憶體。',
 vram_summary_pending='重新啟動後為 %1$s（目前：%2$s）',
 memory_status_title='目前記憶體',
 memory_status_summary='RAM %1$s + %2$s（已用 %3$s）',
 memory_status_summary_off='RAM %1$s（已用 %2$s）',
 reboot_title='需要重新啟動', reboot_message='新的記憶體擴充大小會在平板重新啟動後生效。要立即重新啟動嗎？',
 reboot_now='重新啟動', reboot_later='稍後',
 app_key_entries=['無', '螢幕截圖', '通知', '快速設定', '最近使用的應用程式', '助理', '搜尋', '設定'],
)

L['de'] = dict(
 white_balance_strength_title='Stärke', white_balance_strength_summary='Wie stark sich der Bildschirm an das Umgebungslicht anpasst. 100 % entspricht der Lenovo-Werkseinstellung.',
 app_name='Lenovo-Funktionen', app_summary='Akku, Stift, physische Tastatur, virtueller Speicher',
 charging_category='Laden',
 charging_normal_title='Normal', charging_normal_summary='Lädt bis 100 %',
 charging_limit_title='Bei 80 % stoppen', charging_limit_summary='Laden stoppt bei 80 % und wird unter 76 % fortgesetzt',
 charging_protect_title='Akkuschutz',
 charging_protect_summary='Hält den Akku zwischen 40 % und 60 %, wenn das Tablet dauerhaft angeschlossen ist',
 battery_category='Akku',
 bypass_title='Bypass-Laden',
 bypass_summary='Bei angeschlossenem Ladegerät läuft das Tablet direkt über das Netzteil, ohne den Akku zu laden. Weniger Wärme beim Spielen. Unter 20 % wird wieder geladen.',
 standby_title='Erweiterter Stand-by',
 standby_summary='Versetzt inaktive Apps wenige Minuten nach dem Ausschalten des Bildschirms in den Ruhezustand',
 battery_maintenance_title='Akkulebensdauer optimieren',
 battery_maintenance_summary='Senkt die Ladespannung schrittweise, wenn der Akku altert',
 battery_info_title='Akkustatus', battery_info_summary='Ladezyklen %1$s · Zustand %2$s',
 display_category='Display',
 white_balance_title='Adaptiver Farbton',
 white_balance_summary='Passt die Farbtemperatur des Bildschirms an das Umgebungslicht an',
 pen_category='Stift', pen_settings_title='Lenovo-Stift', pen_settings_summary='Stifttasten, Schreibvibration, Kopplung',
 keyboard_category='Tastatur',
 folio_category='Folio case',
 memory_category='Speicher', vram_title='Speichererweiterung', vram_off='Aus', vram_size='%1$d GB',
 vram_summary='%1$s. Nutzt einen Teil des Speicherplatzes als Arbeitsspeicher für Hintergrund-Apps.',
 vram_summary_pending='%1$s nach Neustart (aktuell: %2$s)',
 memory_status_title='Aktueller Speicher',
 memory_status_summary='RAM %1$s + %2$s (belegt %3$s)',
 memory_status_summary_off='RAM %1$s (belegt %2$s)',
 reboot_title='Neustart erforderlich',
 reboot_message='Die neue Größe der Speichererweiterung gilt nach einem Neustart. Jetzt neu starten?',
 reboot_now='Neu starten', reboot_later='Später',
 app_key_entries=['Keine', 'Screenshot', 'Benachrichtigungen', 'Schnelleinstellungen',
                  'Letzte Apps', 'Assistant', 'Suche', 'Einstellungen'],
)

L['fr'] = dict(
 white_balance_strength_title='Intensité', white_balance_strength_summary='Degré d\'adaptation de l\'écran à la lumière ambiante. 100 % correspond au réglage Lenovo d\'origine.',
 app_name='Fonctions Lenovo', app_summary='Batterie, stylet, clavier physique, mémoire virtuelle',
 charging_category='Charge',
 charging_normal_title='Normale', charging_normal_summary='Charge jusqu\'à 100 %',
 charging_limit_title='Arrêt à 80 %', charging_limit_summary='La charge s\'arrête à 80 % et reprend sous 76 %',
 charging_protect_title='Protection de la batterie',
 charging_protect_summary='Maintient la batterie entre 40 % et 60 % pour une tablette toujours branchée',
 battery_category='Batterie',
 bypass_title='Charge en dérivation',
 bypass_summary='Une fois branchée, la tablette fonctionne directement sur le chargeur sans charger la batterie. Moins de chaleur en jeu. La charge reprend sous 20 %.',
 standby_title='Veille renforcée',
 standby_summary='Met en veille les applications inactives quelques minutes après l\'extinction de l\'écran',
 battery_maintenance_title='Optimisation de la durée de vie',
 battery_maintenance_summary='Réduit progressivement la tension de charge à mesure que la batterie vieillit',
 battery_info_title='État de la batterie', battery_info_summary='Cycles de charge %1$s · Santé %2$s',
 display_category='Écran',
 white_balance_title='Teinte adaptative',
 white_balance_summary='Adapte la température de couleur de l\'écran à la lumière ambiante',
 pen_category='Stylet', pen_settings_title='Stylet Lenovo', pen_settings_summary='Boutons du stylet, vibration d\'écriture, association',
 keyboard_category='Clavier',
 folio_category='Folio case',
 memory_category='Mémoire', vram_title='Extension de mémoire', vram_off='Désactivée', vram_size='%1$d Go',
 vram_summary='%1$s. Utilise une partie du stockage comme mémoire pour les applications en arrière-plan.',
 vram_summary_pending='%1$s après redémarrage (actuellement : %2$s)',
 memory_status_title='Mémoire actuelle',
 memory_status_summary='RAM %1$s + %2$s (utilisée %3$s)',
 memory_status_summary_off='RAM %1$s (utilisée %2$s)',
 reboot_title='Redémarrage requis',
 reboot_message='La nouvelle taille d\'extension de mémoire s\'applique après le redémarrage de la tablette. Redémarrer maintenant ?',
 reboot_now='Redémarrer', reboot_later='Plus tard',
 app_key_entries=['Aucune', 'Capture d\'écran', 'Notifications', 'Réglages rapides',
                  'Applications récentes', 'Assistant', 'Recherche', 'Paramètres'],
)

L['es'] = dict(
 white_balance_strength_title='Intensidad', white_balance_strength_summary='Cuánto se adapta la pantalla a la luz ambiental. 100 % coincide con el ajuste original de Lenovo.',
 app_name='Funciones de Lenovo', app_summary='Batería, lápiz, teclado físico, memoria virtual',
 charging_category='Carga',
 charging_normal_title='Normal', charging_normal_summary='Carga hasta el 100 %',
 charging_limit_title='Detener al 80 %', charging_limit_summary='La carga se detiene al 80 % y se reanuda por debajo del 76 %',
 charging_protect_title='Protección de batería',
 charging_protect_summary='Mantiene la batería entre el 40 % y el 60 % en una tableta siempre enchufada',
 battery_category='Batería',
 bypass_title='Carga en bypass',
 bypass_summary='Con el cargador conectado, la tableta funciona directamente con la energía del cargador sin cargar la batería. Menos calor al jugar. La carga se reanuda por debajo del 20 %.',
 standby_title='Reposo mejorado',
 standby_summary='Suspende las aplicaciones inactivas unos minutos después de apagar la pantalla',
 battery_maintenance_title='Optimizar la vida útil de la batería',
 battery_maintenance_summary='Reduce gradualmente el voltaje de carga a medida que la batería envejece',
 battery_info_title='Estado de la batería', battery_info_summary='Ciclos de carga %1$s · Estado %2$s',
 display_category='Pantalla',
 white_balance_title='Tono de color adaptable',
 white_balance_summary='Ajusta la temperatura de color de la pantalla a la luz ambiental',
 pen_category='Lápiz', pen_settings_title='Lápiz Lenovo', pen_settings_summary='Botones del lápiz, vibración de escritura, vinculación',
 keyboard_category='Teclado',
 folio_category='Folio case',
 memory_category='Memoria', vram_title='Ampliación de memoria', vram_off='Desactivada', vram_size='%1$d GB',
 vram_summary='%1$s. Usa parte del almacenamiento como memoria para las aplicaciones en segundo plano.',
 vram_summary_pending='%1$s tras reiniciar (ahora: %2$s)',
 memory_status_title='Memoria actual',
 memory_status_summary='RAM %1$s + %2$s (en uso %3$s)',
 memory_status_summary_off='RAM %1$s (en uso %2$s)',
 reboot_title='Reinicio necesario',
 reboot_message='El nuevo tamaño de ampliación de memoria se aplica tras reiniciar la tableta. ¿Reiniciar ahora?',
 reboot_now='Reiniciar', reboot_later='Más tarde',
 app_key_entries=['Ninguna', 'Captura de pantalla', 'Notificaciones', 'Ajustes rápidos',
                  'Aplicaciones recientes', 'Asistente', 'Buscar', 'Ajustes'],
)

L['it'] = dict(
 white_balance_strength_title='Intensità', white_balance_strength_summary='Quanto lo schermo si adatta alla luce ambientale. 100% corrisponde all\'impostazione Lenovo originale.',
 app_name='Funzioni Lenovo', app_summary='Batteria, penna, tastiera fisica, memoria virtuale',
 charging_category='Ricarica',
 charging_normal_title='Normale', charging_normal_summary='Ricarica fino al 100%',
 charging_limit_title='Interrompi all\'80%', charging_limit_summary='La ricarica si ferma all\'80% e riprende sotto il 76%',
 charging_protect_title='Protezione batteria',
 charging_protect_summary='Mantiene la batteria tra il 40% e il 60% per un tablet sempre collegato',
 battery_category='Batteria',
 bypass_title='Ricarica bypass',
 bypass_summary='Quando è collegato, il tablet funziona direttamente con l\'alimentatore senza caricare la batteria. Meno calore durante il gioco. La ricarica riprende sotto il 20%.',
 standby_title='Standby avanzato',
 standby_summary='Sospende le app inattive pochi minuti dopo lo spegnimento dello schermo',
 battery_maintenance_title='Ottimizzazione durata batteria',
 battery_maintenance_summary='Riduce gradualmente la tensione di ricarica con l\'invecchiamento della batteria',
 battery_info_title='Stato batteria', battery_info_summary='Cicli di ricarica %1$s · Salute %2$s',
 display_category='Display',
 white_balance_title='Tonalità adattiva',
 white_balance_summary='Adatta la temperatura colore dello schermo alla luce ambientale',
 pen_category='Penna', pen_settings_title='Penna Lenovo', pen_settings_summary='Tasti della penna, vibrazione di scrittura, associazione',
 keyboard_category='Tastiera',
 folio_category='Folio case',
 memory_category='Memoria', vram_title='Estensione memoria', vram_off='Disattivata', vram_size='%1$d GB',
 vram_summary='%1$s. Usa parte dello spazio di archiviazione come memoria per le app in background.',
 vram_summary_pending='%1$s dopo il riavvio (ora: %2$s)',
 memory_status_title='Memoria attuale',
 memory_status_summary='RAM %1$s + %2$s (in uso %3$s)',
 memory_status_summary_off='RAM %1$s (in uso %2$s)',
 reboot_title='Riavvio necessario',
 reboot_message='La nuova dimensione dell\'estensione di memoria si applica dopo il riavvio del tablet. Riavviare ora?',
 reboot_now='Riavvia', reboot_later='Più tardi',
 app_key_entries=['Nessuna', 'Screenshot', 'Notifiche', 'Impostazioni rapide', 'App recenti',
                  'Assistente', 'Cerca', 'Impostazioni'],
)

L['pt-rBR'] = dict(
 white_balance_strength_title='Intensidade', white_balance_strength_summary='Quanto a tela se adapta à luz ambiente. 100% corresponde à configuração original da Lenovo.',
 app_name='Recursos Lenovo', app_summary='Bateria, caneta, teclado físico, memória virtual',
 charging_category='Carregamento',
 charging_normal_title='Normal', charging_normal_summary='Carrega até 100%',
 charging_limit_title='Parar em 80%', charging_limit_summary='O carregamento para em 80% e volta abaixo de 76%',
 charging_protect_title='Proteção da bateria',
 charging_protect_summary='Mantém a bateria entre 40% e 60% em um tablet sempre conectado',
 battery_category='Bateria',
 bypass_title='Carregamento bypass',
 bypass_summary='Conectado ao carregador, o tablet funciona direto na energia do carregador sem carregar a bateria. Menos calor em jogos. O carregamento volta abaixo de 20%.',
 standby_title='Espera aprimorada',
 standby_summary='Coloca apps inativos para dormir alguns minutos após a tela desligar',
 battery_maintenance_title='Otimização da vida útil da bateria',
 battery_maintenance_summary='Reduz a tensão de carregamento aos poucos conforme a bateria envelhece',
 battery_info_title='Status da bateria', battery_info_summary='Ciclos de carga %1$s · Saúde %2$s',
 display_category='Tela',
 white_balance_title='Tom de cor adaptável',
 white_balance_summary='Ajusta a temperatura de cor da tela à luz ambiente',
 pen_category='Caneta', pen_settings_title='Caneta Lenovo', pen_settings_summary='Botões da caneta, vibração de escrita, pareamento',
 keyboard_category='Teclado',
 folio_category='Folio case',
 memory_category='Memória', vram_title='Extensão de memória', vram_off='Desativada', vram_size='%1$d GB',
 vram_summary='%1$s. Usa parte do armazenamento como memória para apps em segundo plano.',
 vram_summary_pending='%1$s após reiniciar (agora: %2$s)',
 memory_status_title='Memória atual',
 memory_status_summary='RAM %1$s + %2$s (em uso %3$s)',
 memory_status_summary_off='RAM %1$s (em uso %2$s)',
 reboot_title='Reinicialização necessária',
 reboot_message='O novo tamanho da extensão de memória é usado após reiniciar o tablet. Reiniciar agora?',
 reboot_now='Reiniciar', reboot_later='Mais tarde',
 app_key_entries=['Nenhuma', 'Captura de tela', 'Notificações', 'Configurações rápidas',
                  'Apps recentes', 'Assistente', 'Pesquisar', 'Configurações'],
)

L['ru'] = dict(
 white_balance_strength_title='Интенсивность', white_balance_strength_summary='Насколько экран подстраивается под окружающее освещение. 100 % соответствует заводской настройке Lenovo.',
 app_name='Функции Lenovo', app_summary='Батарея, перо, физическая клавиатура, виртуальная память',
 charging_category='Зарядка',
 charging_normal_title='Обычная', charging_normal_summary='Заряжает до 100 %',
 charging_limit_title='Остановка на 80 %', charging_limit_summary='Зарядка останавливается на 80 % и возобновляется ниже 76 %',
 charging_protect_title='Защита батареи',
 charging_protect_summary='Держит заряд между 40 % и 60 %, если планшет постоянно подключен',
 battery_category='Батарея',
 bypass_title='Обходная зарядка',
 bypass_summary='При подключении планшет работает напрямую от зарядного устройства, не заряжая батарею. Меньше нагрев в играх. Зарядка возобновляется ниже 20 %.',
 standby_title='Улучшенный режим ожидания',
 standby_summary='Переводит неактивные приложения в сон через несколько минут после выключения экрана',
 battery_maintenance_title='Оптимизация срока службы батареи',
 battery_maintenance_summary='Постепенно снижает напряжение зарядки по мере износа батареи',
 battery_info_title='Состояние батареи', battery_info_summary='Циклы зарядки %1$s · Состояние %2$s',
 display_category='Экран',
 white_balance_title='Адаптивный цветовой тон',
 white_balance_summary='Подстраивает цветовую температуру экрана под окружающее освещение',
 pen_category='Стилус', pen_settings_title='Стилус Lenovo', pen_settings_summary='Кнопки стилуса, вибрация при письме, сопряжение',
 keyboard_category='Клавиатура',
 folio_category='Folio case',
 memory_category='Память', vram_title='Расширение памяти', vram_off='Выкл.', vram_size='%1$d ГБ',
 vram_summary='%1$s. Часть хранилища используется как память для фоновых приложений.',
 vram_summary_pending='%1$s после перезапуска (сейчас: %2$s)',
 memory_status_title='Текущая память',
 memory_status_summary='ОЗУ %1$s + %2$s (занято %3$s)',
 memory_status_summary_off='ОЗУ %1$s (занято %2$s)',
 reboot_title='Требуется перезапуск',
 reboot_message='Новый размер расширения памяти применится после перезапуска планшета. Перезапустить сейчас?',
 reboot_now='Перезапустить', reboot_later='Позже',
 app_key_entries=['Нет', 'Скриншот', 'Уведомления', 'Быстрые настройки', 'Недавние приложения',
                  'Ассистент', 'Поиск', 'Настройки'],
)


def esc(s):
    s = s.replace('&', '&amp;').replace('<', '&lt;').replace('>', '&gt;')
    s = s.replace('\\', '\\\\').replace("'", "\\'").replace('"', '\\"')
    if s.startswith('@') or s.startswith('?'):
        s = '\\' + s
    return s


def attrs(s):
    # a literal % without positional args would be taken as a format string
    literal = re.sub(r'%\d\$[sd]', '', s)
    return ' formatted="false"' if '%' in literal else ''


HEADER = '''<?xml version="1.0" encoding="utf-8"?>
<!--
     SPDX-FileCopyrightText: 2026 The LineageOS Project
     SPDX-License-Identifier: Apache-2.0
-->
<!-- Generated by tools/parts_strings.py; edit the table there. -->
<resources>
'''

en = L['en']
for lang, table in L.items():
    missing = [k for k, _ in KEYS if k not in table] + [a for a in ARRAYS if a not in table]
    assert not missing, (lang, missing)
    out = [HEADER]
    for key, _ in KEYS:
        v = table[key]
        if lang != 'en' and v == en[key] and key not in ('app_name',):
            continue
        out.append(f'    <string name="{key}"{attrs(v)}>{esc(v)}</string>\n')
    for a in ARRAYS:
        items = table[a]
        assert len(items) == len(en[a]), (lang, a)
        out.append(f'    <string-array name="{a}">\n')
        for it in items:
            assert it.count('%') <= 1, it  # arrays are not formatted
            out.append(f'        <item>{esc(it)}</item>\n')
        out.append('    </string-array>\n')
    out.append('</resources>\n')
    d = os.path.join(RES, 'values' if lang == 'en' else 'values-' + lang)
    os.makedirs(d, exist_ok=True)
    with open(os.path.join(d, 'strings.xml'), 'w') as f:
        f.write(''.join(out))
    print(lang, d)
