package Dhulb;

import Dhulb.Exceptions.CompilationException;
import Dhulb.Exceptions.InternalCompilerException;

abstract class Value extends Item {
	FullType type;
	abstract FullType bring() throws CompilationException, InternalCompilerException;
}