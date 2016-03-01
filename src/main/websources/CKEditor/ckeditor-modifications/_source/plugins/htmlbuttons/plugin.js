/*
 * @file HTML Buttons plugin for CKEditor
 * Copyright (C) 2012 Alfonso Mart�nez de Lizarrondo
 * A simple plugin to help create custom buttons to insert HTML blocks
 */

CKEDITOR.plugins.add( 'htmlbuttons',
{
	init : function( editor )
	{

		lang = editor.config.language;

		var buttonsConfig = editor.config.htmlbuttons;
		if (!buttonsConfig)
			return;

		function createCommand( definition )
		{
			return {
				exec: function( editor ) {
					if(definition.name == "Studytable" && lang == "en") {
 					  definition.html = definition.html.replace(/studiepoeng/g, "Credits");       
					}

					editor.insertHtml( definition.html );
				}
			};
		}

		// Create the command for each button
		for(var i=0; i<buttonsConfig.length; i++)
		{
			var button = buttonsConfig[ i ];
			var commandName = button.name;

			if(button.name == "Studytable" && lang == "en") {
		        	button.title = 'Insert study table';
			}

			editor.addCommand( commandName, createCommand(button, editor) );

			editor.ui.addButton( commandName,
			{
				label : button.title,
				command : commandName,
				icon : this.path + button.icon
			});
		}
	} //Init

} );

/**
 * An array of buttons to add to the toolbar.
 * Each button is an object with these properties:
 *	name: The name of the command and the button (the one to use in the toolbar configuration)
 *	icon: The icon to use. Place them in the plugin folder
 *	html: The HTML to insert when the user clicks the button
 *	title: Title that appears while hovering the button
 *
 * Default configuration with some sample buttons:
 */

var studyHtmlBody = "\
  <table class='vrtx-courseplan-table'>\n\
	<tbody>\n\
		<tr>\n\
			<th scope='row' class='small'>\n\
				6. semester</th>\n\
			<td class='large'>\n\
				&nbsp;</td>\n\
			<td class='large'>\n\
				&nbsp;</td>\n\
			<td class='large'>\n\
				&nbsp;</td>\n\
		</tr>\n\
		<tr>\n\
			<th scope='row' class='small'>\n\
				5. semester</th>\n\
			<td class='large'>\n\
				&nbsp;</td>\n\
			<td class='large'>\n\
				&nbsp;</td>\n\
			<td class='large'>\n\
				&nbsp;</td>\n\
		</tr>\n\
		<tr>\n\
			<th scope='row' class='small'>\n\
				4. semester</th>\n\
			<td class='large'>\n\
				&nbsp;</td>\n\
			<td class='large'>\n\
				&nbsp;</td>\n\
			<td class='large'>\n\
				&nbsp;</td>\n\
		</tr>\n\
		<tr>\n\
			<th scope='row' class='small'>\n\
				3. semester</th>\n\
			<td class='large'>\n\
				&nbsp;</td>\n\
			<td class='large'>\n\
				&nbsp;</td>\n\
			<td class='large'>\n\
				&nbsp;</td>\n\
		</tr>\n\
		<tr>\n\
			<th scope='row' class='small'>\n\
				2. semester</th>\n\
			<td class='large'>\n\
				&nbsp;</td>\n\
			<td class='large'>\n\
				&nbsp;</td>\n\
			<td class='large'>\n\
				&nbsp;</td>\n\
		</tr>\n\
		<tr>\n\
			<th scope='row' class='small'>\n\
				1. semester</th>\n\
			<td class='large'>\n\
				&nbsp;</td>\n\
			<td class='large'>\n\
				&nbsp;</td>\n\
			<td class='large'>\n\
				&nbsp;</td>\n\
		</tr>\n\
		<tr>\n\
			<th scope='row' class='small'>\n\
				&nbsp;</th>\n\
			<td class='large'>\n\
				10 studiepoeng</td>\n\
			<td class='large'>\n\
				10 studiepoeng</td>\n\
			<td class='large'>\n\
				10 studiepoeng</td>\n\
		</tr>\n\
	</tbody>\n\
</table>";

CKEDITOR.config.htmlbuttons =  [
	{
		name:'button1',
		icon:'icon1.png',
		html:'<a href="http://www.google.com">Search something</a>',
		title:'A link to Google'
	},
	{
		name:'Studytable',
		icon:'icon_tabellhatt.png',
		html:studyHtmlBody,
		title:'Sett inn studietabell'
	}
];
