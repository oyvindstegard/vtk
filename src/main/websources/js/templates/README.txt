README
======

The files in this folder are Mustache templates (HTML) for JS.

The purpose is to separate presentation (HTML) from logic (JS).

A template-file can be retrieved as a hash array splitted on ###
with each template assigned to a template-name:

vrtxAdmin.templateEngineFacade.get(fileName,
  ["<template-name-1", .., "<template-name-n>"],
deferred);

                                                         
E.g. for frontpage-boxes:
-------------------------

vrtxEditor.multipleFieldsBoxesDeferred = $.Deferred();
vrtxEditor.multipleFieldsBoxesTemplates = vrtxAdmin.templateEngineFacade.get("multiple-fields-boxes", 
  ["string", "html", "radio", "dropdown", "date", "browse",
   "browse-images", "add-remove-move", "button", "add-button",
   "multiple-inputfield"],
vrtxEditor.multipleFieldsBoxesDeferred);

To make HTML with the array you have to call vrtxAdmin.templateEngineFacade.render() with the desired template-name
as first parameter and arguments / a JSON-object as the second parameter.

The properties of the JSON-object can be outputted with {{ }} around them in the template-file.


E.g. make HTML out of "add-remove-move"
---------------------------------------

var html = vrtxAdmin.templateEngineFacade.render(vrtxEditor.multipleFieldsBoxesTemplates["add-remove-move"], {
  clazz: "move",
  buttonText: "Flytt hit"
});

Se more documentation of template writing:
------------------------------------------

https://github.com/janl/mustache.js
http://mustache.github.com/mustache.5.html
