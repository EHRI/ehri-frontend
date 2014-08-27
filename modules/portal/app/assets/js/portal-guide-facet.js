
//From stackoverflow
var urlParams;
(window.onpopstate = function () {
    var match,
        pl     = /\+/g,  // Regex for replacing addition symbol with a space
        search = /([^&=]+)=?([^&]*)/g,
        decode = function (s) { return decodeURIComponent(s.replace(pl, " ")); },
        query  = window.location.search.substring(1);

    urlParams = {};
    while (match = search.exec(query))
       urlParams[decode(match[1])] = decode(match[2]);
})();

$(document).ready(function() {

      // Handling form-submission via links, i.e. search form
      // when facets are clicked
      $('.facet-form').on("change", "input[type='checkbox']", function (e) {
        $(e.target).closest("form").submit();
      });


    $(document).on("change", ".autosubmit", function (e) {
     $(e.target).closest("form").submit();
    });

    //Tooltip
    $('.facet-form').tooltip({
        selector : '[data-toggle="tooltip"]',
        container : 'body'
    });
    $(".facet-form h1 .search").on("click", function(e) {
        e.preventDefault();
        cross = $(this).children(".glyphicon");

        if(cross.hasClass("glyphicon-search")) {
            $(this).parents(".facet-form").children(".facet-search").show();
            cross.removeClass("glyphicon-search").addClass("glyphicon-remove");
        } else {
            $(this).parents(".facet-form").children(".facet-search").hide();
            cross.removeClass("glyphicon-remove").addClass("glyphicon-search");
            $.get(
                form.attr("data-target"),
                {},
                function(data) {
                    form.children("ul.facet-list").html(data).show();
                    if(cross.hasClass("glyphicon-plus-sign")) {
                        cross.removeClass("glyphicon-plus-sign").addClass("glyphicon-minus-sign");
                    }
                },
                "html"
            );
        }
    });
    $(".facet-form .facet-search button[type='submit']").on("click", function(e) {
        e.preventDefault();
        dat = $(this);
        form = dat.parents(".facet-form");
        Q = form.find("input.q").val();
        $.get(
            form.attr("data-target") + "?q=" + Q,
            {},
            function(data) {
                form.children("ul.facet-list").html(data).show();
            },
            "html"
        );
    });
               
    $(".facet-form h1 .more").on("click", function(e) {
        e.preventDefault();
        dat = $(this);
        cross = dat.children(".glyphicon");
        form = dat.parents(".facet-form");
        if(form.find("li.facet").length == 0) {
            $.get(
                form.attr("data-target"),
                {},
                function(data) {
                    form.children("ul.facet-list").html(data).show();
                    if(cross.hasClass("glyphicon-plus-sign")) {
                        cross.removeClass("glyphicon-plus-sign").addClass("glyphicon-minus-sign");
                    }
                },
                "html"
            );
        } else {
            form.children("ul.facet-list").toggle();
            if(cross.hasClass("glyphicon-plus-sign")) {
                cross.removeClass("glyphicon-plus-sign").addClass("glyphicon-minus-sign");
            } else {
                cross.addClass("glyphicon-plus-sign").removeClass("glyphicon-minus-sign");
            }
        }
    });
    $(".facet-form").on("click", " ul.facet-list a[href]", function(e) {
        e.preventDefault();
        var form = $(this),
               list = form.parent().parent("li.facet").children("ul"),
               plus = form.find(".glyphicon");
        if(list.children("li").length > 0) {
            list.toggle();
            plus.toggleClass("glyphicon-minus", list.is(":visible"))
            plus.toggleClass("glyphicon-plus", !list.is(":visible"))
        } else {
            $.get(
                form.attr("href"),
                {},
                function(data) {
                    list.html(data);
                    plus.toggleClass("glyphicon-minus", list.is(":visible"))
                    plus.toggleClass("glyphicon-plus", !list.is(":visible"))

                },
                "html"
            );
        }
    });
    $(".facet-form ul.facet-list").each(function() {
        $(this).data("ajaxready", true).scroll(function() {
            dat = $(this);
            milestone = dat.children(".facet-scroll");

            if($(this).data("ajaxready") == false || milestone.length == 0) {
                return;
            }

            offset = milestone.position();

            if(($(this).height() + 5) > offset.top) {
                $(this).data("ajaxready", false);
                milestone.show();
                $.get(
                    milestone.attr("data-target"),
                    {},
                    function(data) {
                        milestone.remove();
                        dat.append(data);
                        if(dat.find(".facet-scroll") && dat.find(".facet-scroll").length == 1) {
                            dat.data("ajaxready", true);
                        }
                    },
                    "html"
                );
            }
        });
    });


    $(".facet-form").each(function() {
        var form = $(this),
               $param = urlParams[form.attr("data-name")];
        if(form.attr("data-name") in urlParams && $param != "") {
            $(this).find(".search").trigger("click");
            Q = form.find("input.q").val($param);
            $.get(
                form.attr("data-target") + "?q=" + $param,
                {},
                function(data) {
                    form.children("ul.facet-list").html(data).show();
                },
                "html"
            );
        }
    });
});