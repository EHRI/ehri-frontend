$(document).ready(function() {
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
        form = $(this);
        list = form.parent().parent("li.facet").children("ul");
        if(list.children("li").length > 0) {
            list.toggle();
        } else {
            $.get(
                form.attr("href"),
                {},
                function(data) {
                    list.html(data);
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
});