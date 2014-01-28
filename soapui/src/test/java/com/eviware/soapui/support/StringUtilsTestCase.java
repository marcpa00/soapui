/*
 * Copyright 2004-2014 SmartBear Software
 *
 * Licensed under the EUPL, Version 1.1 or - as soon as they will be approved by the European Commission - subsequent
 * versions of the EUPL (the "Licence");
 * You may not use this work except in compliance with the Licence.
 * You may obtain a copy of the Licence at:
 *
 * http://ec.europa.eu/idabc/eupl
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the Licence is
 * distributed on an "AS IS" basis, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either
 * express or implied. See the Licence for the specific language governing permissions and limitations
 * under the Licence.
*/

package com.eviware.soapui.support;

import org.junit.Test;

import static org.hamcrest.CoreMatchers.is;
import static org.junit.Assert.*;

public class StringUtilsTestCase {

    @Test
    public void testUnquote() throws Exception {
        assertEquals("test", StringUtils.unquote("\"test\""));
        assertNull(StringUtils.unquote(null));
        assertEquals("", StringUtils.unquote(""));
        assertEquals("\"test", StringUtils.unquote("\"test"));
        assertEquals("test\"", StringUtils.unquote("test\""));
        assertEquals("test", StringUtils.unquote("test"));
    }

    @Test
    public void testQuote() throws Exception {
        assertNull(StringUtils.quote(null));
        assertEquals("\"\"", StringUtils.quote(""));
        assertEquals("\"test\"", StringUtils.quote("test"));
        assertEquals("\"\"test\"", StringUtils.quote("\"test"));
        assertEquals("\"test\"\"", StringUtils.quote("test\""));
        assertEquals("\"\"\"", StringUtils.quote("\""));
    }

    @Test
    public void testCreateXmlName() throws Exception {
        assertEquals("helloThere", StringUtils.createXmlName("hello there"));
        assertEquals("helloThere", StringUtils.createXmlName("hello ?? there"));
        assertEquals("hello_there", StringUtils.createXmlName("hello_there"));
        assertEquals("helloThere", StringUtils.createXmlName("hello:there"));
        assertEquals("tb_table.column", StringUtils.createXmlName("tb_table.column"));
    }

    @Test
    public void createsXmlNameForStringStartingWithDigit() throws Exception {
        assertThat(StringUtils.createXmlName("15"), is("_15"));
        assertThat(StringUtils.createXmlName("1pt"), is("_1pt"));
	}

	@Test
	public void stringsEqualsWhenOnlyNewlineDifference() throws Exception
	{
		assertTrue( StringUtils.equalsIgnoringLineEndings( "1\r2", "1\n2" ) );
		assertTrue( StringUtils.equalsIgnoringLineEndings( "1\r\n2", "1\n2" ) );
		assertFalse( StringUtils.equalsIgnoringLineEndings( "1\n2", "1\n\n2" ) );
		assertFalse( StringUtils.equalsIgnoringLineEndings( "1\n2", "12" ) );
    }

	@Test
	public void stringsContainsOnlyPlatformLineBreaksAfterNormalization() throws Exception
	{
		String result;
		String stringWithCarriageReturnLinefeed = "abc\r\n123";
		String stringWithLinefeed = "abc\n123";

		String platformLineBreak = System.getProperty( "line.separator" );
		String otherLineBreak;

		if( platformLineBreak.equals( "\r\n" ) )
		{
			otherLineBreak = "\n";
			result = StringUtils.stringNormalizeLineBreak( stringWithLinefeed );
		}
		else
		{
			otherLineBreak = "\r\n";
			result = StringUtils.stringNormalizeLineBreak( stringWithCarriageReturnLinefeed );
		}

		assertTrue( result.contains( platformLineBreak ) );
		assertTrue( ! result.contains( otherLineBreak ) );
	}

}
