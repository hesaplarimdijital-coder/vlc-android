/*
 * ************************************************************************
 *  RemoteAccessUtils.kt
 * *************************************************************************
 * Copyright © 2023 VLC authors and VideoLAN
 * Author: Nicolas POMEPUY
 * This program is free software; you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation; either version 2 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston MA 02110-1301, USA.
 * **************************************************************************
 *
 *
 */

package org.videolan.vlc.util

import kotlinx.coroutines.flow.MutableStateFlow

object RemoteAccessUtils {
    val otpFlow = MutableStateFlow<String?>(null)
    val otpCodeRemoveFlow = MutableStateFlow<String?>(null)

    /** The server's state, by the name of its ServerStatus ("STARTED", "STOPPED"…), for the pages of the Nağmedar interface. */
    val statusFlow = MutableStateFlow("NOT_INIT")

    /** The addresses the server answers on while it runs ("http://192.168.1.20:8080"). */
    val addressesFlow = MutableStateFlow<List<String>>(emptyList())

    /** The addresses of the browsers connected since the server started. */
    val connectionsFlow = MutableStateFlow<List<String>>(emptyList())

}