/*
 * javAPRSlib - https://github.com/ab0oo/javAPRSlib
 *
 * Copyright (C) 2011, 2024 John Gorkos, AB0OO
 *
 * javAPRSlib is free software; you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published
 * by the Free Software Foundation; either version 2 of the License,
 * or (at your option) any later version.
 *
 * javAPRSlib is distributed in the hope that it will be useful, but
 * WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
 * General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this software; if not, write to the Free Software
 * Foundation, Inc., 59 Temple Place, Suite 330, Boston, MA 02111-1307
 * USA
 */
package net.ab0oo.aprs.parser;

/**
 * <p>BadData class.</p>
 *
 * @author john
 * @version $Id: $Id
 */
public class BadData extends APRSData {

    /**
     * <p>Constructor for BadData.</p>
     */
    public BadData() {
        super.setHasFault(true);
    }

    /** {@inheritDoc} */
    @Override
    public String toString() {
        return "Unable to decode this packet";
    }
    
}
