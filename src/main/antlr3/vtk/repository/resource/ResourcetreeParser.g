parser grammar ResourcetreeParser;

options {
  tokenVocab = ResourcetreeLexer;
  output = AST;
}

@header {
package vtk.repository.resource;
}

@members {
    private java.util.List<String> messages = new java.util.ArrayList<String>();

    public void emitErrorMessage(String msg) {
        this.messages.add(msg);
    }

    public java.util.List<String> getErrorMessages() {
        return this.messages;
    }
}

resources
	:	(resourcetypedef)+ |;

resourcetypedef
	:	RESOURCETYPE NAME (parent)? LCB
		  resourcedef
		RCB
		-> ^(RESOURCETYPE ^(NAME (parent)? (resourcedef)?))
	|	include
	;

include	:	INCLUDE FILENAME -> ^(INCLUDE FILENAME);

parent	:	COLON NAME -> ^(PARENT NAME);

resourcedef
	:	(resourceprops)?
		(editrules)?
		(scripts)?
		(services)?
		(viewcomponents)?
		(viewdefinition)?
		(vocabulary)?
		(localization)?
	;

vocabulary
	:	VOCABULARY LCB
		  (vocabularyentry (COMMA vocabularyentry)*)*
		RCB
		-> ^(VOCABULARY (vocabularyentry)*)
	;

vocabularyentry
	:	NAME LCB (vocabularylangentry (COMMA vocabularylangentry)*)  RCB
		-> ^(NAME (vocabularylangentry)*)
	|	NAME COLON NAME LCB (vocabularylangentry (COMMA vocabularylangentry)*)  RCB
		-> ^(NAME ^(NAME (vocabularylangentry)*))
	;
	
vocabularylangentry
	:	NAME COLON LP (vocabularykeyvalue (COMMA vocabularykeyvalue)*) RP
		-> ^(NAME (vocabularykeyvalue)*)
	|
		NAME COLON LP range RP
		-> ^(NAME range)
	;

range	:	RANGE EQ QTEXT -> ^(RANGE QTEXT);
	
vocabularykeyvalue
	: QTEXT (EQ (QTEXT)*)? -> ^(QTEXT ((QTEXT)*)?);
	
localization
	:	LOCALIZATION LCB
		  (localizationentry (COMMA localizationentry)*)*
		RCB
		-> ^(LOCALIZATION (localizationentry)*)
	;

localizationentry
	:	NAME COLON LP (namevaluepair (COMMA namevaluepair)*) RP
		-> ^(NAME (namevaluepair)*)
	|	NAME LCB (localizationentry (COMMA localizationentry)*)  RCB
		-> ^(NAME (localizationentry)*)
	;
    
resourceprops
	:	PROPERTIES LCB
		  (propertytypedef (COMMA propertytypedef)*)*
		RCB
		-> ^(PROPERTIES (propertytypedef)*)
	;

propertytypedef
	:	(derivedpropertytypedef 
	| 	jsonpropertytypedef 
	|	plainpropertytypedef 
	|	binarypropertytypedef)
	;

derivedpropertytypedef
	:	NAME COLON derived (overrides)? (MULTIPLE)? (defaultprop)?
		-> ^(NAME derived (overrides)? (MULTIPLE)? (defaultprop)?)
	|	NAME COLON DERIVED (MULTIPLE)?
		-> ^(NAME ^(DERIVED (MULTIPLE)?))
	;

derived	:	DERIVED LP fieldlist RP EVAL LP evallist RP
		-> ^(DERIVED ^(FIELDS fieldlist) ^(EVAL evallist))
	;

fieldlist
	:	NAME (COMMA NAME)* ->  NAME+;

evallist:	evalelem (PLUS evalelem)* -> evalelem+;

evalelem
    : QTEXT (QUESTION NAME)? -> ^(QTEXT (QUESTION NAME)?)
    | NAME (QUESTION NAME)? -> ^(NAME (QUESTION NAME)?)
    ;
    
nameorqtext
 	:	NAME
 	|	QTEXT -> DQ QTEXT DQ
 	;


defaultprop
	:	DEFAULTPROP NAME -> ^(DEFAULTPROP NAME);

jsonpropertytypedef
	:	NAME COLON JSON (jsonspec)? (MULTIPLE)? (NOEXTRACT)? (INDEXABLEJSON)?
		-> ^(NAME ^(JSON (jsonspec)?) (MULTIPLE)? (NOEXTRACT)? (INDEXABLEJSON)?)
	;

jsonspec:	LP jsonpropspeclist RP -> jsonpropspeclist;

jsonpropspeclist
	:	jsonpropspec (COMMA jsonpropspec)* -> jsonpropspec+;

jsonpropspec
	:	NAME COLON PROPTYPE -> ^(NAME ^(PROPTYPE));

plainpropertytypedef
	:	NAME COLON PROPTYPE (TRIM)? (MULTIPLE)? (REQUIRED)? (NOEXTRACT)? (overrides)?
			(defaultvalue)?
		-> ^(NAME PROPTYPE (TRIM)? (MULTIPLE)? (REQUIRED)? (NOEXTRACT)? (overrides)?
			(defaultvalue)?)
	;

defaultvalue
	:	DEFAULTVALUE LP QTEXT RP -> ^(DEFAULTVALUE QTEXT)
	;

binarypropertytypedef
	:	NAME COLON BINARY -> ^(NAME BINARY);

overrides
	:	OVERRIDES NAME
		-> ^(OVERRIDES NAME)
	;


editruledef
	:	NAME (position)? (LP EDITHINT (COMMA EDITHINT)* RP)?
		-> ^(NAME (position)? (EDITHINT)*)
	|	GROUP NAME namelist (position)? (ORIENTATION)?
		-> ^(GROUP ^(NAME namelist) (position)? (ORIENTATION)?)
	|	NAME TOOLTIP LP (namevaluepair (COMMA namevaluepair)*) RP
		-> ^(NAME ^(TOOLTIP (namevaluepair)*))
	|	NAME LP NOEDIT RP
		-> ^(NAME NOEDIT)
	|	NAME COLON NAME LP EDITHINT RP
		-> ^(NAME ^(NAME EDITHINT))
	;

editrules
	:	EDITRULES LCB
		  (editruledef (COMMA editruledef)*)*
		RCB
		-> ^(EDITRULES (editruledef)*)
	;


position
	:	LP pos NAME RP -> ^(pos NAME);

pos	:	(BEFORE | AFTER);

viewcomponents
	:	VIEWCOMPONENTS LCB
		  (viewcomponent)*
		RCB
		-> ^(VIEWCOMPONENTS (viewcomponent)*)
	;

viewcomponent
	:	NAME (LP paramlist RP)? LCB
		  DEF
		RCB
		-> ^(NAME (DEF)? (paramlist)?)
	;

paramlist
    :   NAME (COMMA NAME)*
        ->  NAME+
    ;

viewdefinition
	:	VIEW LCB
		  (DEF)?
		RCB
		-> ^(VIEW (DEF)?)
	;

scripts	:	SCRIPTS LCB
		  (scriptdef (COMMA scriptdef)*)*
		RCB
		-> ^(SCRIPTS (scriptdef)*)
	;

scriptdef:	NAME SHOWHIDE SCRIPTTRIGGER namelist
		-> ^(NAME ^(SHOWHIDE ^(SCRIPTTRIGGER namelist)))
	|	NAME MULTIPLEINPUTFIELDS
		-> ^(NAME ^(MULTIPLEINPUTFIELDS))
	;

services:	SERVICES LCB
		  (servicedef (COMMA servicedef)*)*
		RCB
		-> ^(SERVICES (servicedef)*)
	;

servicedef
	:	NAME NAME (requires)? (affects)?
		-> ^(NAME ^(NAME (requires)? (affects)?))
	;

requires:	REQUIRES namelist -> ^(REQUIRES namelist);

affects: AFFECTS namelist -> ^(AFFECTS namelist);

namevaluepair
	:	NAME COLON QTEXT
		-> ^(NAME QTEXT)
	;

namelist:	LP NAME (COMMA NAME)* RP -> ^(NAME) ^(NAME)*;

