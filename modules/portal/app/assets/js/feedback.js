// Handle slide-out suggestions form
//

jQuery(function($) {

  var $slider = $('.slide-out-div').tabSlideOut({
    tabHandle: '.handle',
    pathToTabImage: window.TAB_PATH,
    imageHeight: '75px',
    imageWidth: '24px',
    tabLocation: 'left',
    speed: 300,
    action: 'click',
    topPos: '50px',
    fixedPosition: true,
    onSlideOut: function() {
    },
    onSlideIn: function() {
    }
  });

  // handle suggestion form submission... this is a bit
  // gross and fragile.
  var $form = $("#suggestion-form"),
      $submit = $("button[type='submit']", $form),
      $thanks = $(".alert-success", $form),
      $name = $("input[name='name']", $form),
      $text = $("textarea[name='text']", $form),
      $email = $("input[name='email']", $form),
      $emailregexp = /^[a-zA-Z0-9._-]+@[a-zA-Z0-9.-]+\.[a-zA-Z]{2,4}$/;

  $(".slide-out-div").show();

  $name.add($text).add($email).keyup(function(event) {
    var nameval = $.trim($name.val());
    var textval = $.trim($text.val());
    var emailval = $.trim($email.val());
    // email is not reqired, so only check it if filled in
    var emailvalid = (emailval === "" || emailval !== "" && emailval.match($emailregexp));
    //var ok = nameval !== "" && textval !== "" && emailvalid;
    var ok = textval !== "" && emailvalid;
    console.log("ok: ", ok)
    $submit.prop("disabled", !ok);
  });

  $(".modal-close", $form).click(function() {
    $(".slide-out-div > .handle").click();
  });

  $submit.prop("disabled", true).click(function(event) {
    event.preventDefault();
    $submit.prop("disabled", true);
    var $formele = $(this).closest("form");
    $.post($formele.attr("action"), $formele.serialize(), function(data, textStatus) {
      // FIXME: This is rubbish.
      $thanks.width($thanks.parent().width() - ($thanks.outerWidth(true) - $thanks.width()));
      $thanks.slideDown(500, function() {
        setTimeout(function() {
          $(".slide-out-div > .handle").click();
          $text.val("");
          $thanks.slideUp(500);
        }, 1000);
      });
    });
  });
});