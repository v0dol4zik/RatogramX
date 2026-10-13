# Partial quotes

Partial quotes require both the global experiments switch and the partial
quotes switch in Ratogram X settings.

For an ordinary text message, open its message menu and choose **Select text**.
The touched word is selected in the original message. Drag either handle to
change the range, or use the floating **Copy**, **Quote** and **Select All**
actions. Copy is omitted for messages whose content cannot be saved.

While this mode is open, touches select text rather than opening links or
swiping the message, and chat scrolling is blocked. Tap outside the text,
press Back, or use the close button to leave selection mode. Leaving the chat,
recycling the message view or replacing its rendered text also clears it.

The existing **Quote** popup remains available, including for media captions,
translated text and long fragments that extend beyond the visible chat area.
Quotes always use the original message's offsets and permitted entities. An
edited or deleted message cannot submit a stale selection.

## Validation

JVM tests cover Unicode word selection and dragged boundaries around UTF-16
surrogate pairs. Renderer hit testing, handles and gesture interaction require
device verification before release:

- Select single and multiple lines containing links, styled text, spoilers,
  ordinary emoji and custom emoji.
- Check incoming/outgoing bubbles, plain chat style and mixed RTL/LTR text.
- Move each handle on a short word; neither handle should prevent using the other.
- Check a link preview above and below the text and a partially visible message.
- Test Copy restrictions, Quote, Select All, cancellation and normal gestures
  after leaving the mode.
- Edit/delete the source message, rotate the device and disable the experiment.
- Verify the popup fallback for media captions and translated messages.
