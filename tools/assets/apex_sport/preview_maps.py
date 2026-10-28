"""Deterministic fictional map backdrops for design review, never APK assets.

The SVG is editable and has no network, map-tile or third-party image dependency.
Coordinates, roads and place names are illustrative; no real route is represented.
"""

from html import escape


def map_svg(light: bool) -> str:
    """Return a 1920×720 front-facing fictional navigation map as SVG text."""
    if light:
        colors = dict(
            ground="#e7e9e2", block="#d9ddd5", block_edge="#cfd5cc",
            park="#cadbc3", park_line="#b8cdb1", water="#b3d1dc",
            bank="#a7c5cf", local="#f7f8f2", road="#ffffff",
            road_edge="#c3cbc2", label="#707d77", primary="#52655f",
            route="#2679ed", route_edge="#edf5ff", arrow="#ffffff",
        )
    else:
        colors = dict(
            ground="#141c22", block="#1c282e", block_edge="#253138",
            park="#20352e", park_line="#2b4137", water="#142e3c",
            bank="#244350", local="#344149", road="#435058",
            road_edge="#10191f", label="#75878d", primary="#9aadb2",
            route="#4c98ff", route_edge="#142744", arrow="#ffffff",
        )
    parts = [
        '<svg xmlns="http://www.w3.org/2000/svg" width="1920" height="720" viewBox="0 0 1920 720">',
        '<title>APEX SPORT — вымышленная карта для проверки макета</title>',
        '<desc>Редактируемый демонстрационный фон. Не географическая карта и не слой приложения.</desc>',
        f'<rect width="1920" height="720" fill="{colors["ground"]}"/>',
        '<g id="city-blocks">',
    ]
    blocks = [
        "550,20 690,20 706,100 563,121", "721,0 844,0 846,78 735,97",
        "865,0 990,0 984,64 866,76", "1010,0 1146,0 1147,84 1007,65",
        "1168,0 1300,0 1291,107 1170,88", "1320,0 1446,0 1432,130 1311,110",
        "1009,91 1148,109 1148,198 1006,188", "1170,113 1288,131 1277,205 1170,201",
        "1310,135 1428,154 1411,217 1300,211", "864,101 986,85 984,180 872,178",
        "576,143 718,122 738,193 593,227", "600,249 742,214 746,293 615,311",
        "614,337 745,317 727,399 595,415", "585,438 720,419 711,495 570,517",
        "554,541 700,518 710,603 541,629", "531,651 712,628 743,720 513,720",
        "865,212 980,211 977,286 868,281", "1001,215 1060,219 1036,285 996,286",
        "1103,231 1267,238 1243,304 1081,293", "1295,239 1402,246 1377,317 1266,307",
        "1021,330 1155,342 1151,412 1015,405", "1177,345 1242,352 1225,422 1173,416",
        "1263,354 1368,367 1341,437 1248,425", "1020,432 1148,438 1144,510 1015,509",
        "1171,442 1221,448 1223,515 1167,512", "1244,450 1334,464 1317,529 1247,518",
        "1014,535 1143,536 1136,601 1010,604", "1166,540 1308,552 1290,620 1158,604",
        "1010,631 1131,628 1126,720 1018,720", "1157,631 1283,646 1261,720 1152,720",
        "866,344 922,345 920,410 865,409", "868,437 922,438 922,512 879,510",
        "1318,554 1439,570 1417,640 1299,624", "1370,385 1480,400 1455,473 1348,455",
    ]
    for points in blocks:
        parts.append(f'<polygon points="{points}" fill="{colors["block"]}" stroke="{colors["block_edge"]}" stroke-width="1"/>')
    parts.append('</g><g id="parks">')
    parks = [
        "M735 108L842 91L852 188L767 187Z",
        "M1063 221L1091 226L1070 287L1046 286Z",
        "M1362 484L1479 501L1449 550L1338 532Z",
        "M1168 137L1255 149L1246 188L1169 185Z",
    ]
    for path in parks:
        parts.append(f'<path d="{path}" fill="{colors["park"]}"/>')
    parts.append(f'<path d="M751 120L829 106L837 172L775 171M1376 496L1454 512L1439 537" fill="none" stroke="{colors["park_line"]}" stroke-width="2"/>')
    parts.append('</g>')
    river = 'M759 -20C762 79 768 151 787 226C815 335 768 385 783 477C794 547 828 616 852 740L920 740C901 632 860 542 848 473C834 393 888 336 854 221C830 145 824 72 825 -20Z'
    parts.append(f'<path id="river" d="{river}" fill="{colors["water"]}" stroke="{colors["bank"]}" stroke-width="3"/>')
    local_roads = [
        'M516 138L747 102M535 239L753 187M522 326L757 291M521 429L741 398M510 531L722 496M500 641L727 607',
        'M557 0L576 128L593 231L614 322L585 428L562 528L533 643L515 720',
        'M701 0L722 112L747 203L756 299L733 407L717 504L728 620L762 720',
        'M860 87L1000 74L1455 143M857 196L998 202L1433 230',
        'M1001 0L996 303M1158 0L1160 222M1309 0L1286 224M1458 0L1422 231',
        'M863 421L990 418L1391 450M872 523L992 523L1360 546M896 618L993 617L1333 637',
        'M1001 322L998 606L1010 720M1164 338L1157 528L1139 720M1255 348L1235 435L1238 527L1281 644',
        'M1392 344L1347 455L1321 540L1268 720M1498 366L1465 483L1428 555L1380 720',
    ]
    parts.append(f'<g id="local-streets" fill="none" stroke="{colors["local"]}" stroke-width="7" stroke-linejoin="round">')
    parts.extend(f'<path d="{road}"/>' for road in local_roads)
    parts.append('</g>')
    main_roads = [
        'M478 292C635 300 724 308 817 309S1130 310 1508 352',
        'M940 750C949 672 951 607 950 547L950 382C950 342 969 319 1005 302L1072 268Q1096 255 1097 223L1098 -30',
        'M487 62C667 56 724 48 786 37S1005 23 1167 46L1494 101',
    ]
    parts.append('<g id="arterial-streets" fill="none" stroke-linecap="round" stroke-linejoin="round">')
    for road in main_roads:
        parts.append(f'<path d="{road}" stroke="{colors["road_edge"]}" stroke-width="23"/>')
        parts.append(f'<path d="{road}" stroke="{colors["road"]}" stroke-width="17"/>')
    parts.append('</g>')
    route = 'M945 735C949 680 950 629 950 575L950 382C950 342 969 319 1005 302L1072 268Q1096 255 1097 223L1098 84'
    parts.append(f'<path id="route-casing" d="{route}" fill="none" stroke="{colors["route_edge"]}" stroke-width="13" stroke-linecap="round" stroke-linejoin="round"/>')
    parts.append(f'<path id="demo-route" d="{route}" fill="none" stroke="{colors["route"]}" stroke-width="8" stroke-linecap="round" stroke-linejoin="round"/>')
    parts.append(f'<g id="map-labels" font-family="Noto Sans, sans-serif" font-size="15" fill="{colors["label"]}" text-anchor="middle">')
    labels = [
        (792, 152, 'Липовый парк', -5),
        (1030, 174, 'Тихая улица', 0),
        (1170, 322, 'Проспект Мира', 5),
        (1195, 435, 'Северная улица', 4),
        (1190, 535, 'Лесной переулок', 4),
        (1017, 650, 'Набережная', 0),
    ]
    for x, y, label, angle in labels:
        parts.append(f'<text x="{x}" y="{y}" transform="rotate({angle} {x} {y})">{escape(label)}</text>')
    parts.append(f'<text x="820" y="424" font-size="14" letter-spacing="3" fill="{colors["bank"]}" transform="rotate(80 820 424)">РЕКА</text>')
    parts.append('</g>')
    parts.append(f'<g id="navigation-position"><circle cx="950" cy="575" r="32" fill="{colors["route"]}" opacity=".14"/><circle cx="950" cy="575" r="22" fill="{colors["route_edge"]}" opacity=".8"/><path d="M950 551L965 591L950 582L935 591Z" fill="{colors["arrow"]}" stroke="{colors["route"]}" stroke-width="3" stroke-linejoin="round"/></g>')
    parts.append('</svg>')
    return '\n'.join(parts)
