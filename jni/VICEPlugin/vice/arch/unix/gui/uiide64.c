/*
 * uiide64.c
 *
 * Written by
 *  Andreas Boose <viceteam@t-online.de>
 *
 * This file is part of VICE, the Versatile Commodore Emulator.
 * See README for copyright notice.
 *
 *  This program is free software; you can redistribute it and/or modify
 *  it under the terms of the GNU General Public License as published by
 *  the Free Software Foundation; either version 2 of the License, or
 *  (at your option) any later version.
 *
 *  This program is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *  GNU General Public License for more details.
 *
 *  You should have received a copy of the GNU General Public License
 *  along with this program; if not, write to the Free Software
 *  Foundation, Inc., 59 Temple Place, Suite 330, Boston, MA
 *  02111-1307  USA.
 *
 */

#include "vice.h"

#include <stdio.h>
#include <stdlib.h>

#include "lib.h"
#include "resources.h"
#include "uiapi.h"
#include "uiide64.h"
#include "uilib.h"
#include "uimenu.h"
#include "vsync.h"

UI_CALLBACK(set_ide64_image_name)
{
    uilib_select_file((char *)UI_MENU_CB_PARAM, _("IDE64 image"), UILIB_FILTER_ALL);
}

UI_MENU_DEFINE_TOGGLE(IDE64AutodetectSize)
UI_MENU_DEFINE_RADIO(IDE64version4)

static UI_CALLBACK(set_cylinders)
{
    static char input_string[32];

    if (CHECK_MENUS) {
        int autosize;

        resources_get_int("IDE64AutodetectSize", &autosize);

        if (autosize) {
            ui_menu_set_sensitive(w, 0);
        } else {
            ui_menu_set_sensitive(w, 1);
        }
    } else {
        char *msg_string;
        ui_button_t button;
        int i;
        int cylinders;

        vsync_suspend_speed_eval();

        resources_get_int("IDE64Cylinders", &cylinders);

        sprintf(input_string, "%d", cylinders);

        msg_string = lib_stralloc(_("Enter number of cylinders"));
        button = ui_input_string(_("IDE64 cylinders"), msg_string, input_string, 32);
        lib_free(msg_string);
        if (button == UI_BUTTON_OK) {
            i = atoi(input_string);
            if (cylinders > 0 && cylinders <= 1024 && cylinders != i) {
                resources_set_int("IDE64Cylinders", i);
                ui_update_menus();
            }
        }
    }
}

static UI_CALLBACK(set_heads)
{
    static char input_string[32];

    if (CHECK_MENUS) {
        int autosize;

        resources_get_int("IDE64AutodetectSize", &autosize);

        if (autosize) {
            ui_menu_set_sensitive(w, 0);
        } else {
            ui_menu_set_sensitive(w, 1);
        }
    } else {
        char *msg_string;
        ui_button_t button;
        int i;
        int heads;

        vsync_suspend_speed_eval();

        resources_get_int("IDE64Heads", &heads);

        sprintf(input_string, "%d", heads);

        msg_string = lib_stralloc(_("Enter number of heads"));
        button = ui_input_string(_("IDE64 heads"), msg_string, input_string, 32);
        lib_free(msg_string);
        if (button == UI_BUTTON_OK) {
            i = atoi(input_string);
            if (heads > 0 && heads <= 16 && heads != i) {
                resources_set_int("IDE64Heads", i);
                ui_update_menus();
            }
        }
    }
}

static UI_CALLBACK(set_sectors)
{
    static char input_string[32];

    if (CHECK_MENUS) {
        int autosize;

        resources_get_int("IDE64AutodetectSize", &autosize);

        if (autosize) {
            ui_menu_set_sensitive(w, 0);
        } else {
            ui_menu_set_sensitive(w, 1);
        }
    } else {
        char *msg_string;
        ui_button_t button;
        int i;
        int sectors;

        vsync_suspend_speed_eval();

        resources_get_int("IDE64Sectors", &sectors);

        sprintf(input_string, "%d", sectors);

        msg_string = lib_stralloc(_("Enter number of sectors"));
        button = ui_input_string(_("IDE64 sectors"), msg_string, input_string, 32);
        lib_free(msg_string);
        if (button == UI_BUTTON_OK) {
            i = atoi(input_string);
            if (sectors >= 0 && sectors <= 63 && sectors != i) {
                resources_set_int("IDE64Sectors", i);
                ui_update_menus();
            }
        }
    }
}

static ui_menu_entry_t ide64_revision_submenu[] = {
    { N_("Version 3"), UI_MENU_TYPE_TICK, (ui_callback_t)radio_IDE64version4,
      (ui_callback_data_t)0, NULL },
    { N_("Version 4"), UI_MENU_TYPE_TICK, (ui_callback_t)radio_IDE64version4,
      (ui_callback_data_t)1, NULL },
    { NULL }
};

ui_menu_entry_t ide64_submenu[] = {
    { N_("Revision"), UI_MENU_TYPE_NORMAL,
      NULL, NULL, ide64_revision_submenu },
    { "--", UI_MENU_TYPE_SEPARATOR },
    { N_("HD image 1 name..."), UI_MENU_TYPE_NORMAL,
      (ui_callback_t)set_ide64_image_name,
      (ui_callback_data_t)"IDE64Image1", NULL },
    { N_("HD image 2 name..."), UI_MENU_TYPE_NORMAL,
      (ui_callback_t)set_ide64_image_name,
      (ui_callback_data_t)"IDE64Image2", NULL },
    { N_("HD image 3 name..."), UI_MENU_TYPE_NORMAL,
      (ui_callback_t)set_ide64_image_name,
      (ui_callback_data_t)"IDE64Image3", NULL },
    { N_("HD image 4 name..."), UI_MENU_TYPE_NORMAL,
      (ui_callback_t)set_ide64_image_name,
      (ui_callback_data_t)"IDE64Image4", NULL },
    { "--", UI_MENU_TYPE_SEPARATOR },
    { N_("Autodetect image size"), UI_MENU_TYPE_TICK,
      (ui_callback_t)toggle_IDE64AutodetectSize, NULL, NULL },
    { N_("Cylinders..."), UI_MENU_TYPE_NORMAL,
      (ui_callback_t)set_cylinders, NULL, NULL },
    { N_("Heads..."), UI_MENU_TYPE_NORMAL,
      (ui_callback_t)set_heads, NULL, NULL },
    { N_("Sectors..."), UI_MENU_TYPE_NORMAL,
      (ui_callback_t)set_sectors, NULL, NULL },
    { NULL }
};