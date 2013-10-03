/*******************************************************************************
* Copyright 2013 Ivan Shubin http://mindengine.net
* 
* Licensed under the Apache License, Version 2.0 (the "License");
* you may not use this file except in compliance with the License.
* You may obtain a copy of the License at
* 
*   http://www.apache.org/licenses/LICENSE-2.0
* 
* Unless required by applicable law or agreed to in writing, software
* distributed under the License is distributed on an "AS IS" BASIS,
* WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
* See the License for the specific language governing permissions and
* limitations under the License.
******************************************************************************/
package net.mindengine.galen.parser;

import static java.lang.String.format;
import static net.mindengine.galen.parser.Expectations.isDelimeter;
import static net.mindengine.galen.parser.Expectations.isNumeric;
import static net.mindengine.galen.suite.reader.Line.UNKNOWN_LINE;
import net.mindengine.galen.specs.Range;
import net.mindengine.galen.specs.reader.StringCharReader;

public class ExpectRange implements Expectation<Range>{

   
	private enum RangeType {
		NOTHING, APPROXIMATE, LESS_THAN, GREATER_THAN
	}

    @Override
    public Range read(StringCharReader reader) {
        
        RangeType rangeType = RangeType.NOTHING;
        
        char firstNonWhiteSpaceSymbol = reader.firstNonWhiteSpaceSymbol();
        if (firstNonWhiteSpaceSymbol == '~') {
            rangeType = RangeType.APPROXIMATE;
        }
        else if (firstNonWhiteSpaceSymbol == '>') {
        	rangeType = RangeType.GREATER_THAN;
        }
        else if (firstNonWhiteSpaceSymbol == '<') {
        	rangeType = RangeType.LESS_THAN;
        }
        
        Double firstValue = expectDouble(reader);
        
        String text = expectNonNumeric(reader);
        if (text.equals("%")) {
            return createRange(firstValue, rangeType).withPercentOf(readPercentageOf(reader));
        }
        if (text.equals("px")) {
            return createRange(firstValue, rangeType);
        }
        else if (rangeType == RangeType.NOTHING){
            Double secondValue = expectDouble(reader);
            
            Range range = null;
            if (text.equals("to")) {
                range = Range.between(firstValue, secondValue);
            }
            else {
                throw new SyntaxException(UNKNOWN_LINE, msgFor(text));
            }
            
            String end = expectNonNumeric(reader);
            if (end.equals("px")) {
                return range;
            }
            else if (end.equals("%")) {
                return range.withPercentOf(readPercentageOf(reader));
            }
            else throw new SyntaxException(UNKNOWN_LINE, "Missing ending: \"px\" or \"%\"");
        }
        else throw new SyntaxException(UNKNOWN_LINE, msgFor(text));
    }

    private Range createRange(Double firstValue, RangeType rangeType) {
        if (rangeType == RangeType.APPROXIMATE) {
            Double delta = Math.abs(firstValue) / 100;
            if (delta < 1.0) {
                delta = 1.0;
            }
            
            return Range.between(firstValue - delta, firstValue + delta);
        }
        else if (rangeType == RangeType.GREATER_THAN) {
        	return Range.greaterThan(firstValue);
        }
        else if (rangeType == RangeType.LESS_THAN) {
        	return Range.lessThan(firstValue);
        }
        else {
            return Range.exact(firstValue);
        }
    }

    private String readPercentageOf(StringCharReader reader) {
        String firstWord = expectNonNumeric(reader);
        if (firstWord.equals("of")) {
            String valuePath = expectAnyWord(reader).trim();
            if (valuePath.isEmpty()) {
                throw new SyntaxException(UNKNOWN_LINE, "Missing value path for relative range");
            }
            else return valuePath;
        }
        else throw new SyntaxException(UNKNOWN_LINE, "Missing value path for relative range");
    }

    private String expectNonNumeric(StringCharReader reader) {
        boolean started = false;
        char symbol;
        StringBuffer buffer = new StringBuffer();
        while(reader.hasMore()) {
            symbol = reader.next();
            if (started && isDelimeter(symbol)) {
                break;
            }
            else if (isNumeric(symbol)) {
                reader.back();
                break;
            }
            else if (!isDelimeter(symbol)) {
                buffer.append(symbol);
                started = true;
            }
        }
        return buffer.toString();
    }
    
    private String expectAnyWord(StringCharReader reader) {
        boolean started = false;
        char symbol;
        StringBuffer buffer = new StringBuffer();
        while(reader.hasMore()) {
            symbol = reader.next();
            if (started && isDelimeter(symbol)) {
                break;
            }
            else if (!isDelimeter(symbol)) {
                buffer.append(symbol);
                started = true;
            }
        }
        return buffer.toString();
    }

    private Double expectDouble(StringCharReader reader) {
        boolean started = false;
        char symbol;
        boolean hadPointAlready = false;
        StringBuffer buffer = new StringBuffer();
        while(reader.hasMore()) {
            symbol = reader.next();
            if (started && isDelimeter(symbol)) {
                break;
            }
            else if (symbol == '.') {
                if (hadPointAlready) {
                    throw new SyntaxException(UNKNOWN_LINE, msgFor("" + symbol)); 
                }
                hadPointAlready = true;
                buffer.append(symbol);
            }
            else if (isNumeric(symbol)) {
                buffer.append(symbol);
                started = true;
            }
            else if (started) {
                reader.back();
                break;
            }
        }
        String doubleText = buffer.toString();
        
        try {
            return Double.parseDouble(doubleText);
        }
        catch (Exception e) {
            throw new SyntaxException(UNKNOWN_LINE, format("Cannot parse range value: \"%s\"", doubleText), e);
        }
    }

    private String msgFor(String text) {
        return String.format("Cannot parse range: \"%s\"", text);
    }
         
}
