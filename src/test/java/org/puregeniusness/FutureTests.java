package org.puregeniusness;

import io.vertx.core.Future;
import io.vertx.core.Promise;
import io.vertx.core.impl.future.PromiseImpl;
import io.vertx.core.json.JsonObject;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

public class FutureTests {

  /**
   * This test is designed to demonstrate that the value within onSuccess is identical
   * to the result field of the value in onComplete when the future it is called on succeeds
   * <p>
   * The print statements are to demonstrate that both onSuccess and onComplete have run
   * while the assertSame verifies that the data references the same object. You can see
   * from this test that onSuccess is identical to the result field of a succeeded future in onComplete
   */
  @Test
  public void onSuccessVsOnComplete() {
    JsonObject completedFutureResult = new JsonObject().put("name", "sam");

    Future<JsonObject> ft = Future.succeededFuture(completedFutureResult);

    ft.onSuccess(data -> {
      System.out.println("in onSuccess with result: " + data);
      assertSame(completedFutureResult, data);
    });

    ft.onComplete(data -> {
      if (data.succeeded()) {
        System.out.println("in onComplete with result: " + data.result());
        assertSame(completedFutureResult, data.result());
      } else if (data.failed()) {
        // this will NEVER run when the future it is called on succeeds
        fail();
      }
    });

    // onFailure NEVER runs when the future it is called on succeeds
    ft.onFailure(e -> fail());
  }

  /**
   * This test is designed to demonstrate that the throwable within onFailure is identical
   * to the cause field of the value in onComplete when the future it is called on fails
   * <p>
   * The print statements are to demonstrate that both onFailure and onComplete have run
   * while the assertSame verifies that the data references the same object. You can see
   * from this test that onFailure is identical to the cause field of a failed future in onComplete.
   */
  @Test
  public void onFailureVsOnComplete() {
    Throwable failureFutureThrowable = new Throwable("Something went wrong!");

    Future<JsonObject> ft = Future.failedFuture(failureFutureThrowable);

    // onSuccess NEVER runs when the future it is called on fails
    ft.onSuccess(data -> fail());

    ft.onComplete(data -> {
      if (data.succeeded()) {
        // this will NEVER run when the future it is called on fails
        fail();
      } else if (data.failed()) {
        System.out.println("in onComplete with result: " + data.cause().getMessage());
        assertSame(failureFutureThrowable, data.cause());
      }
    });

    ft.onFailure(data -> {
      System.out.println("in onFailure with result: " + data.getMessage());
      assertSame(failureFutureThrowable, data);
    });
  }

  /**
   * This test demonstrates that calling the future method on a promise actually returns the same reference
   * If you look into Vert.x you will see the below code within PromiseImpl
   *
   * @Override public Future<T> future() {
   * return this;
   * }
   */
  @Test
  public void promiseAndFuture() {
    Promise<JsonObject> promise = Promise.promise();
    Future<JsonObject> ft = promise.future();
    assertSame(ft, promise);
  }

  /**
   * This test is designed to show that when calling Future.future(promise -> ?) the value within the lambda is
   * a promise object. This can be seen within the method signature where Future.future accepts one argument
   * of type Handler<Promise<T>>. Because of the Handler.handle method being defined as accepting Handler<E>
   * where E is defined as Promise<T>, we know the value within the handle method is of type Promise<T>. The result
   * of Future.ft(promise -> ...) is the completed promise returned as a future by calling promise.future()
   */
  @Test
  public void creatingAPromiseFromFutureInterface() {
    Future<String> future = Future.future(promise -> {
      promise.complete("I will print second");
      System.out.println("I will print first");
      assertTrue(promise instanceof PromiseImpl);
    });

    future.onSuccess(System.out::println);
  }

  /**
   * This test is designed to demonstrate that the promise within Future.future will not complete or fail on its own
   */
  @Test
  public void incompleteFuture() {
    Future<String> future = Future.future(promise -> {
      System.out.println("I will print");
    });

    future.onSuccess(result -> {
      System.out.println("in onSuccess. I will not print");
      fail();
    });

    future.onFailure(throwable -> {
      System.out.println("in onFailure. I will not print");
      fail();
    });

    assertFalse(future.isComplete());
  }

  @Test
  public void promiseTryComplete() {
    Promise<String> promise = Promise.promise();
    promise.tryComplete("result1");
    assertFalse(promise.tryComplete("result2"));
  }

  @Test
  public void promise2() {
    Promise<String> promise = Promise.promise();
    promise.complete("result1");

    Exception exception = assertThrows(IllegalStateException.class, () -> {
      promise.complete("result2");
    });
  }

  /**
   * We know now that onFailure is called with the throwable from a FailedFuture. Because of this, we can show
   * that compose returns a failed future when called with only the success mapper. If a FailedFuture were not
   * being returned from compose onFailure would not be executed here
   */

  @Test
  public void composeExample1() {
    Promise<String> promise1 = Promise.promise();
    Future<String> future1 = promise1.future();

    Promise<String> promise2 = Promise.promise();
    Future<String> future2 = promise2.future();

    future1.compose(result -> {
      System.out.println("I will not be printed");
      return future2;
    }).onFailure(System.out::println);

    promise1.fail("error");
  }

  /**
   * Chained successful composes example
   */
  @Test
  public void composeExample2() {
    Promise<String> promise1 = Promise.promise();
    Future<String> future1 = promise1.future();

    Promise<String> promise2 = Promise.promise();
    Future<String> future2 = promise2.future();
    future2.onSuccess(result -> System.out.println("Result in future2 onSuccess: " + result));

    future1.compose(result -> {
      System.out.println(result);
      promise2.complete(result);
      return future2;
    });

    promise1.complete("Sam");
  }

  /**
   * This example shows how the compose method has an overloaded version that allows for your own custom
   * error mapper instead of using the default Future::failedFuture when only one argument is passed
   */
  @Test
  public void composeExample3() {
    Promise<String> promise1 = Promise.promise();
    Future<String> future1 = promise1.future();

    Promise<String> promise2 = Promise.promise();
    Future<String> future2 = promise2.future();
    future2.onSuccess(System.out::println);

    future1.compose(result -> {
      System.out.println(result);
      promise2.complete(result);
      return future2;
    }, throwable -> {
      System.out.println(throwable.getMessage());
      return Future.failedFuture(throwable);
    });

    promise1.complete("Sam");
  }

  /**
   * The recover method is similar to how onFailure is to onComplete. Under the hood, it actually calls the
   * compose method with (Future::succeededFuture, yourErrorMapper); Recall that compose has an overload that accepts
   * arguments for both a success and failure mapper. This allows you to build logic for when a future it is called on
   * fails while propagating the result to the next future if the future recover on succeeds
   */
  @Test
  public void recoverFuture() {
    Promise<String> promise1 = Promise.promise();
    Future<String> future1 = promise1.future();

    Promise<String> promise2 = Promise.promise();
    Future<String> future2 = promise2.future();
    future2.onSuccess(System.out::println);

    future1.compose(result -> {
      System.out.println(result);
      promise2.complete(result);
      return future2;
    }, throwable -> {
      System.out.println(throwable.getMessage());
      return Future.failedFuture(throwable);
    });

    promise1.complete("Sam");
  }
}
