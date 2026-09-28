// Redux store and typed hooks.
import { configureStore } from '@reduxjs/toolkit';
import { useDispatch, useSelector } from 'react-redux';
import auth from './authSlice';
import cart, { CartState, loadCart, saveCart } from './cartSlice';

export function createStore(preloaded?: { cart: CartState }) {
  return configureStore({ reducer: { auth, cart }, preloadedState: preloaded });
}

// The app's store starts from the saved cart and writes it back whenever the lines change.
export const store = createStore({ cart: loadCart() });
let savedLines = store.getState().cart.lines;
store.subscribe(() => {
  const { cart: now } = store.getState();
  if (now.lines !== savedLines) {
    savedLines = now.lines;
    saveCart(now);
  }
});

export type AppStore = ReturnType<typeof createStore>;
export type RootState = ReturnType<AppStore['getState']>;
export type AppDispatch = AppStore['dispatch'];

export const useAppDispatch = useDispatch.withTypes<AppDispatch>();
export const useAppSelector = useSelector.withTypes<RootState>();
