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
      $text.focus()
    },
    onSlideIn: function() {
    }
  });

  // handle suggestion form submission... this is a bit
  // gross and fragile.
  var $formContainer = $("#suggestions"),
      $cancel = $("a#cancel", $formContainer),
      $form = $("form#suggestion-form")
      $submit = $("button[type='submit']", $form),
      $thanks = $(".alert-success", $formContainer),
      $text = $("textarea[name='text']", $form);

  $(".slide-out-div").show();

  $form.validate({
    showErrors: function(em, el) {}
  })

  $form.keyup(function(event) {
    $submit.prop("disabled", !$form.valid());
  });

  $(".modal-close", $formContainer).click(function() {
    $(".slide-out-div > .handle").click();
  });

  $cancel.click(function(e) {
    e.preventDefault();
    $text.val("")
    $(".slide-out-div > .handle").click();
  })

  $submit.click(function(event) {
    event.preventDefault();
    $submit.prop("disabled", true);
    $.post($form.attr("action"), $form.serialize(), function(data, textStatus) {
      console.log(data)
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