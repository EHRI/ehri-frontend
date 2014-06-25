$(document).ready(function(){
	var geoNamesUsername = 'EhriAdmin';

	remotes = {
		city : {
				template : Handlebars.compile('<b>{{name}}</b><span class="text-muted"> - {{countryCode}} <small>({{adminName}})</small></span>'),
				selected : function(el, data) {
					form = el.parents(".address-form");
					form.find("[name$='countryCode']").select2("val", data.countryCode);
					form.find("[name$='firstdem']").val(data.adminName);
				},
				BH : new Bloodhound({
						datumTokenizer: function (d) {
      						return Bloodhound.tokenizers.whitespace(d); 
						},
						queryTokenizer: Bloodhound.tokenizers.whitespace,
						remote: {
							url : 'http://api.geonames.org/searchJSON?name_startsWith=%QUERY&maxRows=10&username=' + geoNamesUsername + '&lang=en&featureClass=P&style=long',
							filter : function(parsedResponse) {
								var result = [];
								for (var i=0; i<parsedResponse.geonames.length; i++) {
									var geonameId = parsedResponse.geonames[i].geonameId;
									result.push({
										name: parsedResponse.geonames[i].name,
										value: parsedResponse.geonames[i].name,
										geonameId: geonameId,
										countryCode: parsedResponse.geonames[i].countryCode,
										lat: parsedResponse.geonames[i].lat,
										lng: parsedResponse.geonames[i].lng,
										bbox: parsedResponse.geonames[i].bbox,
										adminName : parsedResponse.geonames[i].adminName1
									});
								}
								return result;
							}
						}
					})
			}
	}

	remotes["city"].BH.initialize();

	function th(that) {
		remoteName = that.attr('data-remote');
		if(typeof remotes[remoteName] !== "undefined" && that.hasClass("tt-input") === false && that.hasClass("tt-hint") === false) {
			that.typeahead(
				null,
				{
					name: remoteName,
					source: remotes[remoteName].BH.ttAdapter(),
					templates: {
						suggestion : remotes[remoteName].template
					}
				}
			);

			that.on('typeahead:selected', function(e, data) {
				that = $(this);
				remoteName = that.attr('data-remote');
				remotes[remoteName].selected(that, data);
			});

		}
	}

	/**
	 * Initialize typeahead.js
	 */
	$('.typeahead').each(function() {
		that = $(this);
		th(that);
	});

	$("[data-prefix='addressArea'] .add-inline-element").on("click", function() {
		$('.typeahead').each(function() {
			that = $(this);
			th(that);
		});
	});

});