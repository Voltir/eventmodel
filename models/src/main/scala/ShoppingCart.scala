import eventmodel.*
import eventmodel.html.Html
import eventmodel.markdown.Markdown
import java.nio.file.Path

/** A small board in the book's own domain, exercising all four patterns.
  *
  * Copy this file to start a new one: define the elements, group the slices
  * into chapters, list the swimlanes, and give it a `@main` of its own.
  *
  * The model is written the way a person would write it at a whiteboard, with
  * no hints about what is wrong with it. It happens to contain a real gap --
  * `Cart Items` exposes a field no event carries -- and finding that is the
  * validator's job, not the author's.
  */
object ShoppingCart:

  private val cartId    = Field("cartId", "UUID")
  private val productId = Field("productId", "UUID")
  // "how many, in this action" vs "how many are in the cart right now".
  // One name for both makes a screen look like it needs no input at all.
  private val quantity       = Field("quantity", "Int")
  // A sum, not a copy: say so, or it looks like it has no source at all.
  private val quantityInCart =
    Field("quantityInCart", "Int", derivedFrom = List("quantity"))
  private val unitPrice = Field("unitPrice", "Money")
  private val available = Field("availableQuantity", "Int")

  private val cartScreen = Screen("Cart", "Line items, quantities, running total")

  // --- events ---------------------------------------------------------------

  private val itemAdded     = Event("Item Added", List(cartId, productId, quantity, unitPrice))
  private val itemRemoved   = Event("Item Removed", List(cartId, productId))
  private val cartSubmitted = Event("Cart Submitted", List(cartId))

  private val inventoryChanged  = Event("Inventory Changed", List(productId, available))
  private val inventoryReserved = Event("Inventory Reserved", List(cartId, productId, quantityInCart))

  // Streams own their events: a swimlane is a stream boundary, so each event
  // lives in exactly one of these.
  private val cartStream = Swimlane(
    "cart",
    "The shopper's own cart, from first item to submission",
    events = List(itemAdded, itemRemoved, cartSubmitted)
  )

  private val inventoryStream = Swimlane(
    "inventory",
    "Stock levels and reservations",
    events = List(inventoryChanged, inventoryReserved)
  )

  // --- commands -------------------------------------------------------------

  private val addItem    = Command("Add Item", List(cartId, productId, quantity, unitPrice))
  private val removeItem = Command("Remove Item", List(cartId, productId))
  private val submitCart = Command("Submit Cart", List(cartId))
  private val reserveInventory = Command("Reserve Inventory", List(cartId, productId, quantityInCart))

  // --- slices ---------------------------------------------------------------

  private val addItemSlice = Slice.StateChange(
    name = "Add Item",
    command = addItem,
    events = List(itemAdded),
    screen = Some(cartScreen),
    rules = List(
      Gwt(
        "an item can be added to an empty cart",
        givenEvents = Nil,
        whenCommand = addItem,
        thenOutcome = Outcome.Events(List(itemAdded))
      )
    )
  )

  private val removeItemSlice = Slice.StateChange(
    name = "Remove Item",
    command = removeItem,
    events = List(itemRemoved),
    screen = Some(cartScreen)
  )

  private val cartContentsSlice = Slice.StateView(
    name = "Cart Contents",
    events = List(itemAdded, itemRemoved),
    readModel = ReadModel(
      "Cart Items",
      List(
        cartId,
        productId,
        Field("productName", "String"),
        Field("gomg", "String"),
        quantityInCart,
        unitPrice
      )
    ),
    screen = Some(cartScreen),
    rules = List(
      Gt(
        "added items appear in the cart",
        givenEvents = List(itemAdded),
        thenExpectation = "the read model shows the same data as the event"
      )
    )
  )

  // --- alternative flow: what happens when submission fails -----------------
  // The book models the good case first and puts error cases in their own
  // model, reached from a marker under the slice they branch from.

  private val submissionFailed =
    Event("Cart Submission Failed", List(cartId, Field("reason", "String")))
  private val cartAbandoned = Event("Cart Abandoned", List(cartId))

  private val retrySubmit =
    Command("Retry Submit", List(cartId, Field("reason", "String")))
  private val abandonCart = Command("Abandon Cart", List(cartId))

  private val errorScreen =
    Screen("Submission Error", "What went wrong, and whether to retry")

  val submitCartError: EventModel = EventModel(
    name = "Submit Cart Error",
    description =
      "What the cart does when submission fails, including the rule that three " +
        "failures abandon it.",
    chapters = List(
      Chapter.flat(
        "Failure",
        // Without this the error screen has nothing behind it, and the
        // validator says so: a form cannot pre-fill a cartId out of thin air.
        Slice.StateView(
          name = "Failed Submissions",
          events = List(submissionFailed),
          readModel = ReadModel("Failed Submissions", List(cartId, Field("reason", "String"))),
          screen = Some(errorScreen),
          rules = List(
            Gt(
              "a failure shows the reason it failed",
              givenEvents = List(submissionFailed),
              thenExpectation = "the read model shows the cart and why submission failed"
            )
          )
        ),
        Slice.StateChange(
          name = "Retry Submit",
          command = retrySubmit,
          events = List(submissionFailed),
          screen = Some(errorScreen),
          rules = List(
            Gwt(
              "a failed submission can be retried",
              givenEvents = List(submissionFailed),
              whenCommand = retrySubmit,
              thenOutcome = Outcome.Events(List(submissionFailed))
            )
          )
        ),
        Slice.StateChange(
          name = "Abandon Cart",
          command = abandonCart,
          events = List(cartAbandoned),
          screen = Some(errorScreen),
          rules = List(
            Gwt(
              "three failures abandon the cart",
              givenEvents = List(submissionFailed, submissionFailed, submissionFailed),
              whenCommand = abandonCart,
              thenOutcome = Outcome.Events(List(cartAbandoned))
            )
          )
        )
      )
    ),
    swimlanes = List(
      Swimlane(
        "cart",
        "The shopper's own cart, on the failure path",
        events = List(submissionFailed, cartAbandoned)
      )
    )
  )

  private val submitCartSlice = Slice.StateChange(
    name = "Submit Cart",
    command = submitCart,
    events = List(cartSubmitted),
    screen = Some(cartScreen),
    altFlows = List(
      AltFlow("submission fails", submitCartError)
    ),
    rules = List(
      Gwt(
        "an empty cart cannot be submitted",
        givenEvents = Nil,
        whenCommand = submitCart,
        thenOutcome = Outcome.Rejected("the cart has no items")
      ),
      Gwt(
        "a cart with items can be submitted",
        givenEvents = List(itemAdded),
        whenCommand = submitCart,
        thenOutcome = Outcome.Events(List(cartSubmitted))
      )
    )
  )

  private val stockLevelSlice = Slice.Translation(
    name = "Stock Level Changed",
    external = ExternalEvent(
      "Stock Level Changed",
      source = "Inventory System",
      fields = List(productId, available)
    ),
    style = TranslationStyle.ToInternalEvent,
    events = List(inventoryChanged)
  )

  private val reserveInventorySlice = Slice.Automation(
    name = "Reserve Inventory",
    readModel = ReadModel("Submitted Carts", List(cartId, productId, quantityInCart)),
    processor = Processor(
      "Reservation Process",
      "Reserves stock for each line of a submitted cart"
    ),
    command = reserveInventory,
    events = List(inventoryReserved),
    trigger = Trigger.ByEvent
  )

  // --- the model ------------------------------------------------------------

  val cart: EventModel = EventModel(
    name = "Shopping Cart",
    description =
      "A worked example in the four Event Modeling patterns: State Change, " +
        "State View, Automation and Translation.",
    chapters = List(
      Chapter(
        "Shopping",
        "Everything between picking items and reserving stock for them",
        subChapters = List(
          SubChapter("Items", List(addItemSlice, removeItemSlice, cartContentsSlice)),
          SubChapter("Submission", List(submitCartSlice)),
          SubChapter("Inventory", List(stockLevelSlice, reserveInventorySlice))
        )
      )
    ),
    swimlanes = List(cartStream, inventoryStream)
  )

  /** `Submit Cart Error` is not listed here -- it is reachable as an
    * alternative flow from the Submit Cart slice, and the board finds it.
    */
  val board: Board = Board(
    name = "Shopping",
    description = "One business context per model, read left to right.",
    models = List(cart)
  )

// Named `renderX` rather than `x`: a main named `shoppingCart` would generate
// a class differing only in case from `ShoppingCart`, which collides on
// case-insensitive filesystems.
@main def renderShoppingCart(): Unit =
  Html.render(ShoppingCart.board, Path.of("out"))
  Markdown.render(ShoppingCart.board, Path.of("out"))
