package Dhulb;

import Dhulb.Exceptions.NotImplementedException;

class Function {
	FullType retType;
	String name;
	FullType[] args;
    //starts before the function definition (whitespace before it allowed), consumes everything up to the closing curly brace, inclusive
	static Function from() throws NotImplementedException {
		throw new NotImplementedException();
	}
	
	
}