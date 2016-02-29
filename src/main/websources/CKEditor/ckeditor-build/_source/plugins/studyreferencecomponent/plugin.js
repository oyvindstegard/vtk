CKEDITOR.plugins.add( 'studyreferencecomponent',
{
	init: function( editor )
	{
		var pluginName = 'studyreferencecomponent';
		editor.addCommand( pluginName, new CKEDITOR.dialogCommand( pluginName ) );
 
		editor.ui.addButton( 'Studyreferencecomponent',
		{
			label: editor.lang.studyreferencecomponent.title,
			command: pluginName,
			icon: this.path + 'images/icon_lenkehatt.png'
		} );
 
		CKEDITOR.dialog.add( pluginName, function( editor )
		{
			return {
				title : editor.lang.studyreferencecomponent.title,
				minWidth : 400,
				minHeight : 100,
				contents :
				[
					{
						id : 'general',
						label : 'Settings',
						elements :
						[
							{
								type : 'select',
								id : 'type',
								label : 'Type',
								items : 
								[
									[ 'emne'],
									[ 'emnegruppe'],
									[ 'semester'],
									[ 'studieprogram'],
									[ 'studieretning']
								],
								commit : function( data )
								{
									data.studietype = this.getValue();
								}
							},
							{
								type : 'text',
								id : 'value',
								label : editor.lang.studyreferencecomponent.valuefield,
								validate : CKEDITOR.dialog.validate.notEmpty( editor.lang.studyreferencecomponent.errormessage ),
								required : true,
								commit : function( data )
								{
									data.tekstverdi = this.getValue();
								}
							}
						]
					}
				],
				onOk : function()
				{
					var dialog = this,
						data = {},
						output = "${include:ref referencetype=[TYPE] id=[VALUE]}";

					this.commitContent( data );
					output = output.replace("TYPE", data.studietype);
					output = output.replace("VALUE", data.tekstverdi);						

					editor.insertHtml( output );
				}
			};
		} );
	}
} );
