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

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
/**
 * <p>Parser class.</p>
 *
 * @author johng
 *This is the code parser for AX25 UI packets that are traditionally used in APRS networks, in TNC2
 * format.  TNC2 format is defined as:
 * SOURCE&gt;DESTIN,VIA,VIA:payload
 * In APRS packets, the first character of the payload is the Data Type Identifier, which is the key for
 * further parsing of the message.  This class parses raw TNC2 packets and returns instances of APRSPackets
 * @version $Id: $Id
 */
public class Parser {
    private static final Pattern altitudePattern = Pattern.compile(".*/A=(\\d{6}).*");

	
	/**
	 * <p>main.</p>
	 *
	 * @param args an array of {@link java.lang.String} objects
	 */
	public static void main( String[] args ) {
		if ( args.length > 0 ) {
			try {
				APRSPacket packet = Parser.parse(args[0]);
				System.out.println("From:	"+packet.getSourceCall());
				System.out.println("To:	"+packet.getDestinationCall());
				System.out.println("Via:	"+packet.getDigiString());
				System.out.println("DTI:	"+packet.getDti());
				System.out.println("Valid:	"+packet.isAprs());
				InformationField data = packet.getAprsInformation();
				System.out.println("Data:	" + data);
				if ( packet.isAprs() && data != null) {
					System.out.println("    Type:	" + data.getClass().getName());
					System.out.println("    Messaging:	" + data.canMessage);
					System.out.println("    Comment:	" + data.getComment());
					System.out.println("    Extension:	" + data.getExtension());
				}
			} catch ( Exception ex ) {
				System.err.println("Unable to parse:  "+ex);
				ex.printStackTrace();
			}
		}
	}
    
	/**
	 * <p>parse.</p>
	 *
	 * @param packet inbound packet as a string
	 * @return APRSPacket a fully parsed APRSPacket object
	 * @throws java.lang.Exception Generic "I failed"
	 */
	public static APRSPacket parse(final String packet) throws Exception {
        int cs = packet.indexOf('>');
		if ( cs < 0 ) {
			throw new UnparsablePacketException("Not a valid AX25-style packet");
		}
        String source = packet.substring(0,cs).toUpperCase();
        int ms = packet.indexOf(':');
        String digiList = packet.substring(cs+1,ms);
        String[] digiTemp = digiList.split(",");
        String dest = digiTemp[0].toUpperCase();
        ArrayList<Digipeater> digis = Digipeater.parseList(digiList, false);
        String body = packet.substring(ms+1);
        APRSPacket ap = parseBody(source, dest, digis, body);
        ap.setOriginalString(packet);
        return ap;
    }

    
	/**
	 * <p>parseAX25.</p>
	 *
	 * @param packet inbound packet as a byte[]
	 * @return APRSPacket fully parsed APRS packet object
	 * @throws java.lang.Exception Generic "I failed" ** TODO ** make this a meaningful exception
	 */
	public static APRSPacket parseAX25(byte[] packet) throws Exception {
	    int pos = 0;
	    String dest = new Callsign(packet, pos).toString();
	    pos += 7;
	    String source = new Callsign(packet, pos).toString();
	    pos += 7;
	    ArrayList<Digipeater> digis = new ArrayList<Digipeater>();
	    while ((packet[pos - 1] & 1) == 0) {
		    Digipeater d =new Digipeater(packet, pos);
		    digis.add(d);
		    pos += 7;
	    }
	    if (packet[pos] != 0x03 || packet[pos+1] != -16 /*0xf0*/)
		    throw new IllegalArgumentException("control + pid must be 0x03 0xF0!");
	    pos += 2;
	    String body = new String(packet, pos, packet.length - pos);
	    return parseBody(source, dest, digis, body);
    }

    /**
     * <p>parseBody.</p>
     *
     * @param source Source callsign
     * @param dest Destination callsing, may be part of a compressed postion
     * @param digis array of digipeaters this packet has passed through
     * @param body msg body of the on air message
     * @throws java.lang.Exception
     *
     * This is the core packet parser.  It parses the entire "body" of the APRS Packet,
     * starting with the Data Type Indicator in position 0.
     * @return a {@link net.ab0oo.aprs.parser.APRSPacket} object
     */
    public static APRSPacket parseBody(String source, String dest, ArrayList<Digipeater> digis, String body) throws Exception {
		APRSPacket packet = new APRSPacket(source,dest,digis, body.getBytes());
        byte[] msgBody = body.getBytes();
        byte dti = msgBody[0];
		// get the invalid crap out of the way right away.
		if ( (dti >='A' && dti <= 'S') || 
		     (dti >='U' && dti <= 'Z') ||
			 (dti >='0' && dti <= '9') ) {
			return packet;
		}
		InformationField infoField = packet.getAprsInformation();
		int cursor = 0;
        switch ( dti ) {
        	case '/':
        	case '@':
				// These have timestamps, so we need to parse those, advance the cursor, and then look for
				// the position data
				TimeField timeField = new TimeField(msgBody, cursor);
				infoField.addAprsData(APRSTypes.T_TIMESTAMP, timeField);
				cursor = timeField.getLastCursorPosition();
				PositionField pf = new PositionField(msgBody, dest, cursor+1);
				infoField.addAprsData(APRSTypes.T_POSITION,pf);
				infoField.setDataExtension(pf.getExtension());
				cursor = pf.getLastCursorPosition();
				if ( pf.getPosition().getSymbolCode() == '_' ) {
					// this is a weather station, but it might NOT be transmitting WEATHER DATA
					WeatherField wf = WeatherParser.parseWeatherData(msgBody, cursor);
					wf.setType(APRSTypes.T_WX);
					infoField.addAprsData(APRSTypes.T_WX, wf);
					cursor = wf.getLastCursorPosition();
				} else {
					byte[] slice = Arrays.copyOfRange(msgBody, cursor, msgBody.length-1);
					Matcher matcher = altitudePattern.matcher(new String(slice));
					if (matcher.matches()) {
						pf.getPosition().setAltitude(Integer.parseInt(matcher.group(1)));
					}
				}
				break;
	    	case '!':
        	case '=':
        	case '`':
        	case '\'':
        	case '$':
        		if ( body.startsWith("$ULTW") ) {
        			// Ultimeter II weather packet
        		} else {
					// these are non-timestamped packets with position.
					pf = new PositionField(msgBody, dest, cursor+1);
					cursor = pf.getLastCursorPosition();
					infoField.addAprsData(APRSTypes.T_POSITION, pf );
					infoField.setDataExtension(pf.getExtension());
					if ( cursor >= msgBody.length ) {
						// this is a position-only packet, time to leave
						break;
					}
					if ( pf.getPosition().getSymbolCode() == '_' && msgBody.length > 20) {
						// with weather...
						WeatherField wf = WeatherParser.parseWeatherData(msgBody, cursor + 1);
						infoField.addAprsData(APRSTypes.T_WX, wf);
						cursor = wf.getLastCursorPosition();
					} else {
						Matcher matcher;
						try {
							byte[] slice = Arrays.copyOfRange(msgBody, cursor, msgBody.length-1);
							matcher = altitudePattern.matcher(new String(slice));
						} catch ( IllegalArgumentException iae ) {
							iae.printStackTrace();
							BadData bd = new BadData();
							String msg = "Wandered off the end of the msg body at cursor pos "+cursor;
							bd.setFaultReason(msg);
							infoField.addAprsData(APRSTypes.T_UNSPECIFIED, bd);
							return packet;
						}
						if (matcher.matches()) {
							pf.getPosition().setAltitude(Integer.parseInt(matcher.group(1)));
						}
					}
        		}
    			break;
        	case ':':
        		infoField.addAprsData(APRSTypes.T_MESSAGE, new MessagePacket(msgBody,dest));
				break;

    		case ';':
				ObjectField of;
    			if (msgBody.length > 29) {
    				//System.out.println("Parsing an OBJECT");
					of = new ObjectField(msgBody);
    				infoField.addAprsData(APRSTypes.T_OBJECT, of);
					cursor = of.getLastCursorPosition();
					if (cursor > msgBody.length - 1 ) {
						System.err.println("Ran off the end:");
						System.err.println(msgBody);
						break;
					}
					byte[] slice = Arrays.copyOfRange(msgBody, cursor, msgBody.length-1);
					packet.setComment(new String(slice, StandardCharsets.UTF_8));
    			} else {
					of = new ObjectField("null", false, null, "foo");
					String reason="Object packet body too short ("+msgBody.length+") for valid object";
					System.err.println(reason);
    				of.setHasFault(true); // too short for an object
					of.setFaultReason(reason);
    			}
    			break;
    		case '>':
//				packet.setType(APRSTypes.T_STATUS);
    			break;
    		case '<':
//				packet.setType(APRSTypes.T_STATCAPA);
    			break;
    		case '?':
//				packet.setType(APRSTypes.T_QUERY);
    			break;
    		case ')':
				ItemField itemField = new ItemField(msgBody);
				infoField.addAprsData(APRSTypes.T_ITEM, itemField);
				cursor = itemField.getLastCursorPosition();
    			break;
    		case 'T':
    			if (msgBody.length > 18) {
    				//System.out.println("Parsing TELEMETRY");
    				//parseTelem(bodyBytes);
    			} else {
//    				packet.setHasFault(true); // too short
//					packet.setFaultReason("Packet too short for telemetry packet");
	   			}
    			break;
    		case '#': // Peet Bros U-II Weather Station
    		case '*': // Peet Bros U-II Weather Station
    		case '_': // Weather report without position
				WeatherField  wf = WeatherParser.parseWeatherData(msgBody, cursor);
				infoField.addAprsData(APRSTypes.T_WX, wf);
				cursor = wf.getLastCursorPosition();
    			break;
    		case '{':
//				packet.setType(APRSTypes.T_USERDEF);
    			break;
    		case '}': // 3rd-party
//				packet.setType(APRSTypes.T_THIRDPARTY);
    			break;

    		default:
//    			packet.setHasFault(true); // UNKNOWN!
//				packet.setFaultReason("Unknown fault reason.  Sorry.");
    			break;

        }
		return packet;
    }
    
}
