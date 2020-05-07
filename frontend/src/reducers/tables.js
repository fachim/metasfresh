import { Map as iMap, List as iList } from 'immutable';

import * as types from '../constants/ActionTypes';

export const tableState = {
  windowType: null,
  viewId: null,
  docId: null,
  tabId: null,
  selected: iList(),
  rows: iList(),
  columns: iMap(),
  activeSort: false,
  headerProperties: {},
  headerElements: iList(),
  emptyText: null,
  emptyHint: null,
  page: 0,
  firstRow: 0,
  size: 0,
  orderBy: [],
  pageLength: 0,
  queryLimit: 0,
  queryLimitHit: false,
  dataPending: false,
  dataError: false,

  // includedTabsInfo
  allowCreateNew: true,
  allowCreateNewReason: null,
  allowDelete: true,
  stale: false,
};

// we store the length of the tables structure for the sake of testing and debugging
export const initialState = iMap({ length: 0 });

export default function table(state = initialState, action) {
  switch (action.type) {
    // CRUD
    case types.CREATE_TABLE: {
      const { id, data } = action.payload;
      const newLength = state.get('length') + 1;

      return state.withMutations((map) => {
        map
          .set('length', newLength)
          .set(id, tableState)
          .mergeDeepIn([id], iMap(data));
      });
    }
    case types.UPDATE_TABLE: {
      const { id, data } = action.payload;

      return state.mergeDeepIn([id], data);
    }
    case types.DELETE_TABLE: {
      const { id } = action.payload;

      return state.delete(id);
    }
    default: {
      return state;
    }
  }
}
